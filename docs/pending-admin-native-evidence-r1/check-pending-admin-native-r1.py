"""Read frozen owned-QA evidence only; never connects to PostgreSQL/ADB or executes tests.

Run after four normal native phases, exact secondary-user cleanup, and graceful fixture stop.
Raw accepted packets and Room bundles stay in the evidence directory. Only the resulting
runtime-proof.json, sanitized-provenance.json and packet-index.json are intended for curation.
Pass --build-log PATH only for a completed build that ran the required Gradle tasks; without
that explicit evidence this script makes no JVM/lint/build success claim.
"""
from pathlib import Path
import argparse
import collections
import copy
import hashlib
import json
import re
import shutil
import sqlite3
import tempfile
import uuid
import xml.etree.ElementTree as ET

PROGRAM = Path('D:/Code/Documents/Career-Portfolio-2026')
REPO = Path('D:/Code/Worktrees/Closing-Count-Portfolio')
PHASES = ['settings', 'team', 'catalog', 'lifecycle']
COUNTS = [1, 3, 4, 9]
PHASE_COUNTS = [1, 2, 1, 5]
KINDS = ['SETTINGS', 'TEAM_CREATE', 'TEAM_UPDATE', 'CATALOG'] + ['LIFECYCLE'] * 5
ORIGIN = 'http://10.0.2.2:4371'
CLASS = 'com.closingcount.app.OnlinePendingAdminNativeInstrumentedTest'
METHOD = 'nativeAdministrativeRecoveryContinuesThroughNormalExplicitActions'
HEX = re.compile(r'[a-f0-9]{64}')
BAD_LOG = ['FAILURES!!!', 'INSTRUMENTATION_FAILED', 'shortMsg=', 'Process crashed',
           'AssumptionViolated', 'STATUS_CODE: -3', 'INSTRUMENTATION_CODE: 0']
SECRET_KEYS = {'password', 'password_hash', 'passwordhash', 'token', 'token_hash',
               'access_token', 'accesstoken', 'refresh_token', 'authorization', 'session_id'}


def need(condition, message):
    if not condition:
        raise AssertionError(message)


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'duplicate JSON field')
        result[key] = value
    return result


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique_pairs)


def log(path):
    body = path.read_bytes()
    return body.decode('utf-16' if body[:2] in [b'\xff\xfe', b'\xfe\xff'] else 'utf-8-sig')


def sha(body):
    return hashlib.sha256(body).hexdigest()


def identifier(value):
    need(isinstance(value, str) and str(uuid.UUID(value)) == value, 'noncanonical UUID')
    return value


def no_secrets(value, forbid_receipt_fingerprint=False):
    if isinstance(value, dict):
        for key, child in value.items():
            need(key.lower() not in SECRET_KEYS, 'credential field in collected evidence')
            need(not (forbid_receipt_fingerprint and key == 'fingerprint'), 'receipt fingerprint was collected')
            no_secrets(child, forbid_receipt_fingerprint)
    elif isinstance(value, list):
        for child in value:
            no_secrets(child, forbid_receipt_fingerprint)


def raw_json_nodes(text):
    """Preserve actual Android JSON escaping/key order for payload SHA comparison."""
    decoder = json.JSONDecoder(object_pairs_hook=unique_pairs)
    spans = {}

    def skip(pos):
        while pos < len(text) and text[pos] in ' \t\r\n':
            pos += 1
        return pos

    def parse(pos, path):
        pos = skip(pos); start = pos
        if text[pos] == '{':
            result = {}; pos = skip(pos + 1)
            while text[pos] != '}':
                key, pos = decoder.raw_decode(text, pos)
                need(isinstance(key, str) and key not in result, 'invalid object field')
                pos = skip(pos); need(text[pos] == ':', 'missing JSON colon')
                result[key], pos = parse(pos + 1, path + (key,))
                pos = skip(pos)
                if text[pos] == '}':
                    break
                need(text[pos] == ',', 'missing JSON comma'); pos = skip(pos + 1)
            pos += 1
        elif text[pos] == '[':
            result = []; pos = skip(pos + 1)
            while text[pos] != ']':
                item, pos = parse(pos, path + (len(result),)); result.append(item)
                pos = skip(pos)
                if text[pos] == ']':
                    break
                need(text[pos] == ',', 'missing JSON array comma'); pos = skip(pos + 1)
            pos += 1
        else:
            result, pos = decoder.raw_decode(text, pos)
        spans[path] = text[start:pos]
        return result, pos

    value, end = parse(0, ())
    need(skip(end) == len(text), 'trailing JSON input')
    return value, spans


def safe_artifact(out, name):
    need(isinstance(name, str) and Path(name).name == name and '/' not in name and '\\' not in name,
         'artifact name leaves evidence directory')
    return out / name


def frozen_manifest(folder, phase, installation, proof):
    manifest = read(folder / 'manifest.json')
    need(manifest['phase'] == phase and manifest['user'] == installation['user']
         and manifest['uid'] == proof['uid'] and manifest['run'] == installation['run']
         and manifest['appStopped'] is True and manifest['sessionCredentialsCollected'] is False,
         'invalid frozen secondary manifest')
    need(manifest['appUidQuiescent'] is True and manifest['guardMode'] == 'exact-owned-user-package-uid-normal-teardown'
         and manifest['liveStopClaim'] is False, 'normal-teardown collection guard missing')
    package_rows = log(folder / 'frozen-guard-package.txt')
    package_uid = [int(match[1]) for line in package_rows.splitlines()
                   if (match := re.fullmatch(r'package:com\.closingcount\.app uid:(\d+)', line.strip()))]
    need(package_uid == [proof['uid']], 'authoritative secondary package UID differs')
    process_rows = log(folder / 'frozen-guard-processes.txt')
    for line in process_rows.splitlines():
        if not line.strip() or line.strip().startswith('PID '):
            continue
        row = re.fullmatch(r'\s*(\d+)\s+(\d+)\s+(\S+)\s+(.+?)\s*', line)
        need(row is not None, 'unparsed process row in frozen collection guard')
        need(not (int(row[2]) == proof['uid'] and not row[3].startswith('Z')), 'secondary app UID writer was still live')
    collected = {}
    for group in ['inbox', 'room']:
        files = manifest[group]
        need(isinstance(files, list) and len({f['name'] for f in files}) == len(files), 'repeated frozen artifact')
        names = set()
        for record in files:
            name = record['name']
            if group == 'inbox':
                need(re.fullmatch(r'[a-f0-9]{64}\.json', name), 'nonaccepted file in inbox collection')
            else:
                need(name in {'closing_count.db', 'closing_count.db-wal', 'closing_count.db-shm'},
                     'unexpected Room/session file')
            path = safe_artifact(folder / group, name); body = path.read_bytes()
            need(len(body) == record['bytes'] and sha(body) == record['sha256'], 'frozen artifact hash mismatch')
            if group == 'inbox':
                need(sha(body) == name[:-5], 'inbox filename differs from packet hash')
            names.add(name)
        need({p.name for p in (folder / group).iterdir() if p.is_file()} == names,
             'unlisted frozen artifact')
        collected[group] = names
    need('closing_count.db' in collected['room'], 'frozen Room database missing')
    need(len(collected['inbox']) == proof['inboxCount'], 'frozen packet count differs from phase proof')
    return manifest


def room_snapshot(folder):
    """Read a disposable copy so WAL recovery cannot change the frozen evidence bundle."""
    cache = Path('D:/Code/ToolCache/PortfolioQA').resolve()
    need(cache.is_dir(), 'owned QA cache missing')
    with tempfile.TemporaryDirectory(prefix='ccs-admin-native-oracle-', dir=str(cache)) as temporary:
        scratch = Path(temporary).resolve()
        need(scratch.parent == cache and scratch.name.startswith('ccs-admin-native-oracle-'), 'unsafe scratch path')
        for name in ['closing_count.db', 'closing_count.db-wal', 'closing_count.db-shm']:
            source = folder / name
            if source.exists():
                shutil.copyfile(source, scratch / name)
        connection = sqlite3.connect((scratch / 'closing_count.db').as_uri() + '?mode=ro', uri=True)
        connection.row_factory = sqlite3.Row
        try:
            connection.execute('PRAGMA query_only=ON')
            need(connection.execute('PRAGMA integrity_check').fetchall()[0][0] == 'ok', 'Room integrity check failed')
            need(connection.execute('PRAGMA user_version').fetchone()[0] == 10, 'unexpected Room schema')
            names = [r[0] for r in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")]
            expected = {'online_closings', 'online_entries', 'online_operations', 'online_lifecycle_operations',
                        'online_recovered_admin', 'online_recovery_claims'}
            need({n for n in names if n.startswith('online_')} == expected, 'unexpected online tables')
            rows = {}
            for table in expected:
                rows[table] = [dict(row) for row in connection.execute('SELECT * FROM "' + table + '"')]
            legacy = {}
            for table in names:
                if table.startswith(('online_', 'sqlite_')) or table in {'android_metadata', 'room_master_table'}:
                    continue
                quoted = '"' + table.replace('"', '""') + '"'
                values = [dict(row) for row in connection.execute('SELECT * FROM ' + quoted)]
                legacy[table] = sorted(values, key=lambda r: json.dumps(r, sort_keys=True, ensure_ascii=True))
            rows['legacyRowsSha256'] = sha(json.dumps(legacy, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode())
            return rows
        finally:
            connection.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=PROGRAM / 'evidence/closing-count-pending-admin-native-r1')
    parser.add_argument('--build-log', type=Path)
    args = parser.parse_args(); out = args.out.resolve()
    need(out.is_dir() and out.is_relative_to((PROGRAM / 'evidence').resolve()), 'unexpected evidence directory')
    verification = read(out / 'verification.json')
    need(verification['passed'] is True and verification['cleanupErrors'] == []
         and verification['normalVerificationTests'] == 4 and verification['cumulativeCasesCounts'] == COUNTS,
         'four normal phases/cleanup not verified')
    for key in ['deviceSettingsUsersAndCurrentUserRestored', 'primaryRoomFilesUnchanged', 'packetsSeededDirectlyIntoInbox']:
        need(verification[key] is True, 'host preservation/setup invariant missing')
    for key in ['documentsPickerExercised', 'physicalPhoneAccessed', 'completePt07Acceptance']:
        need(verification[key] is False, 'native acceptance limit invalid')
    need(read(out / 'device-baseline.json') == read(out / 'device-after.json'), 'owner/device baseline changed')
    need((out / 'primary-room-before.sha256').read_bytes() == (out / 'primary-room-after.sha256').read_bytes(),
         'owner Room files changed')
    installation = read(out / 'secondary-installation.json'); identifier(installation['run'])
    need(installation['user'] > 0 and installation['physicalPhoneAccessed'] is False, 'secondary user guard missing')
    proofs = verification['phases']; processes = verification['processes']
    need([p['phase'] for p in proofs] == PHASES and [p['phase'] for p in processes] == PHASES, 'phase order differs')
    need(len({p['uid'] for p in proofs}) == 1 and len({p['pid'] for p in proofs}) == 4, 'expected four isolated invocations')
    need(len({p['legacyBeforeSha256'] for p in proofs} | {p['legacyAfterSha256'] for p in proofs}) == 1,
         'legacy logical hash changed between phases')
    fixture = read(out / 'fixture-identity.json'); no_secrets(fixture)
    expected_purpose = out.name.removeprefix('closing-count-')
    need(re.fullmatch(r'pending-admin-native-r[1-9][0-9]*', expected_purpose) is not None
         and fixture['ownedDatabase'] is True and fixture['retained'] is True
         and fixture['fixturePurpose'] == expected_purpose, 'wrong/unowned fixture namespace')
    actor = identifier(fixture['admin']); outlet = identifier(fixture['adminOutlet'])
    scope = ORIGIN + '|' + actor + '|' + outlet
    all_cases = proofs[-1]['allCases']
    need(len(all_cases) == 9 and [r['kind'] for r in all_cases] == KINDS
         and [r['phase'] for r in all_cases] == ['settings', 'team', 'team', 'catalog'] + ['lifecycle'] * 5,
         'nine intended recovery cases missing')
    need(len({identifier(r[k]) for r in all_cases for k in ['originalId', 'newId']}) == 18,
         'source/recovered IDs overlap')
    need(len({identifier(r['backupId']) for r in all_cases}) == 9
         and len({r['packetSha256'] for r in all_cases}) == 9
         and len({identifier(r['sourceDevice']) for r in all_cases}) == 1, 'packet identity contract differs')
    snapshots = {}; manifests = {}
    for index, (phase, proof, process) in enumerate(zip(PHASES, proofs, processes)):
        need(proof == read(out / ('native-pending-admin-' + phase + '.json')), 'saved proof/status differ')
        need(proof['passed'] is True and proof['phase'] == phase and proof['run'] == installation['run']
             and proof['user'] == installation['user'] and proof['uid'] // 100000 == installation['user']
             and proof['actor'] == actor and proof['outlet'] == outlet, 'phase actor/user/run invalid')
        need(proof['inboxCount'] == COUNTS[index] and proof['allCases'] == all_cases[:COUNTS[index]], 'cumulative cases changed')
        start = 0 if index == 0 else COUNTS[index - 1]
        need(proof['phaseCases'] == all_cases[start:COUNTS[index]] and len(proof['phaseCases']) == PHASE_COUNTS[index],
             'phase-specific cases differ')
        for key in ['legacyUnchanged', 'allFinalSendsUsedProductionUi', 'packetsSeededDirectlyIntoInbox']:
            need(proof[key] is True, 'native invariant missing')
        for key in ['documentsPickerExercised', 'physicalPhoneAccessed', 'completePt07Acceptance']:
            need(proof[key] is False, 'native evidence overclaims acceptance')
        need(HEX.fullmatch(proof['legacyBeforeSha256']) is not None, 'invalid legacy hash')
        observed = read(out / (phase + '-configuration.json'))
        font = 2.0 if phase == 'team' else 1.0; night = 'dark' if phase == 'team' else 'light'
        need(observed['phase'] == phase and observed['user'] == installation['user']
             and observed['requestedFontScale'] == font and observed['requestedNightMode'] == night
             and float(observed['observedSettingFontScale']) == font
             and observed['observedNightMode'] == ('Night mode: yes' if phase == 'team' else 'Night mode: no')
             and observed['physicalPhoneAccessed'] is False, 'host configuration differs')
        need(abs(proof['actualFontScale'] - font) < 0.01 and proof['actualNightMode'] == night, 'activity configuration differs')
        need(process == read(out / (phase + '-process.json')) and process['exitCode'] == 0
             and process['normalVerification'] is True and process['nativeHandleCaptured'] is True
             and process['appPid'] == proof['pid'] and process['appUid'] == proof['uid']
             and process['cumulativeCases'] == COUNTS[index] and process['phaseCases'] == PHASE_COUNTS[index],
             'normal invocation/process evidence invalid')
        stdout = log(safe_artifact(out, process['stdout'])); stderr = log(safe_artifact(out, process['stderr']))
        need('OK (1 test)' in stdout and CLASS in stdout and METHOD in stdout, 'normal single native test missing')
        need(not any(bad in stdout + stderr for bad in BAD_LOG), 'instrumentation error/skip present')
        lines = [line.split('ccsAdminNativeProof=', 1)[1] for line in stdout.splitlines()
                 if line.startswith('INSTRUMENTATION_STATUS: ccsAdminNativeProof=')]
        need(len(lines) == 1 and json.loads(lines[0], object_pairs_hook=unique_pairs) == proof, 'stdout proof mismatch')
        frozen = out / 'snapshots' / phase
        manifests[phase] = frozen_manifest(frozen, phase, installation, proof)
        need({r['name'] for r in manifests[phase]['inbox']} == {r['packetSha256'] + '.json' for r in proof['allCases']},
             'frozen inbox is not exact cumulative packet set')
        snapshots[phase] = room_snapshot(frozen / 'room')
    need(read(out / 'frozen-secondary-manifest.json') == manifests['lifecycle'], 'final manifest alias differs')
    for group in ['inbox', 'room']:
        need({p.name for p in (out / group).iterdir() if p.is_file()} == {r['name'] for r in manifests['lifecycle'][group]},
             'final bundle alias file set differs')
        for row in manifests['lifecycle'][group]:
            body = (out / group / row['name']).read_bytes()
            need(len(body) == row['bytes'] and sha(body) == row['sha256'], 'final bundle alias hash differs')
    need(len({s['legacyRowsSha256'] for s in snapshots.values()}) == 1, 'independent legacy Room rows changed')

    packets = {}; packet_index = []
    for case in all_cases:
        need(case['originalRecorded'] is False and case['newRecorded'] is True
             and case['duplicateActionBlocked'] is True and case['heldThroughContinuationAndRecreation'] is True,
             'native case acceptance missing')
        need(HEX.fullmatch(case['sourcePayloadSha256']) is not None, 'invalid source payload checksum')
        body = (out / 'inbox' / (case['packetSha256'] + '.json')).read_bytes()
        need(sha(body) == case['packetSha256'] and len(body) == case['packetBytes'], 'packet bytes differ')
        packet, spans = raw_json_nodes(body.decode('utf-8')); no_secrets(packet)
        need(set(packet) == {'format', 'schemaVersion', 'backupId', 'createdAt', 'sourceDeviceId', 'origin', 'account', 'outlet', 'closings', 'admin'}
             and packet['format'] == 'closing-count-online-pending-backup' and packet['schemaVersion'] == 1
             and packet['backupId'] == case['backupId'] and packet['sourceDeviceId'] == case['sourceDevice']
             and packet['origin'] == ORIGIN and packet['createdAt'] == '2026-10-24T00:00:00Z', 'packet envelope differs')
        need(packet['account'] == {'id': actor, 'username': fixture['adminUsername'], 'name': fixture['adminDisplayName']}
             and packet['outlet'] == {'id': outlet, 'name': 'Admin native QA outlet'}, 'packet owner/outlet differs')
        if case['kind'] == 'LIFECYCLE':
            need(packet['admin'] == [] and len(packet['closings']) == 1, 'lifecycle packet shape differs')
            closing = packet['closings'][0]; operation = closing['lifecycle']
            need(closing['id'] == case['closing'] and closing['date'] == case['date'] == '2026-07-08'
                 and closing['state'] == case['beforeState'] and closing['generation'] == case['beforeGeneration']
                 and closing['revision'] == case['beforeRevision'] and closing['entries'] == closing['operations'] == [],
                 'lifecycle packet base differs')
            need(operation == {'operationId': case['originalId'], 'action': case['action'], 'generation': case['beforeGeneration'],
                               'expectedRevision': case['beforeRevision'], 'reason': case['reason'], 'status': 'SENT', 'wasSent': True},
                 'lifecycle packet intent differs')
            raw_payload = '{"action":' + spans[('closings', 0, 'lifecycle', 'action')] + ',"reason":' + spans[('closings', 0, 'lifecycle', 'reason')] + '}'
        else:
            need(packet['closings'] == [] and len(packet['admin']) == 1, 'admin packet shape differs')
            operation = packet['admin'][0]
            need(set(operation) == {'kind', 'operationId', 'expectedRevision', 'status', 'wasSent', 'payload'}
                 and operation['kind'] == case['kind'] and operation['operationId'] == case['originalId']
                 and operation['status'] == 'SENT' and operation['wasSent'] is True, 'admin source operation differs')
            need(operation['expectedRevision'] == (None if case['kind'] == 'TEAM_CREATE' else case['expectedRevision']),
                 'admin source revision differs')
            raw_payload = spans[('admin', 0, 'payload')]
        need(sha(raw_payload.encode('utf-8')) == case['sourcePayloadSha256'], 'source payload SHA differs from accepted bytes')
        packets[case['originalId']] = packet
        packet_index.append({key: case[key] for key in ['phase', 'kind', 'originalId', 'newId', 'backupId', 'sourceDevice', 'packetSha256', 'packetBytes', 'sourcePayloadSha256']})

    for phase, snapshot in snapshots.items():
        count = COUNTS[PHASES.index(phase)]; current = all_cases[:count]
        admins = {r['originalOperationId']: r for r in snapshot['online_recovered_admin']}
        claims = {r['originalOperationId']: r for r in snapshot['online_recovery_claims']}
        need(set(admins) == {r['originalId'] for r in current if r['kind'] != 'LIFECYCLE'}
             and set(claims) == {r['originalId'] for r in current if r['kind'] == 'LIFECYCLE'}, 'Room provenance set differs')
        need(snapshot['online_operations'] == snapshot['online_lifecycle_operations'] == [], 'work queue not acknowledged')
        for case in current:
            if case['kind'] == 'LIFECYCLE':
                row = claims[case['originalId']]
                need(row['scope'] == scope and row['fingerprint'] == case['packetSha256']
                     and row['closingId'] == case['closing'] and row['recoveredOperationId'] == case['newId'], 'Room lifecycle provenance differs')
            else:
                row = admins[case['originalId']]; payload = json.loads(row['payloadJson'], object_pairs_hook=unique_pairs); no_secrets(payload)
                need(row['scope'] == scope and row['fingerprint'] == case['packetSha256'] and row['operationId'] == case['newId']
                     and row['kind'] == case['kind'] and row['group'] == ('TEAM' if case['kind'].startswith('TEAM_') else case['kind'])
                     and row['expectedRevision'] == (None if case['kind'] == 'TEAM_CREATE' else case['expectedRevision'])
                     and row['active'] == 0 and row['status'] == 'SENT' and row['wasSent'] == 1 and row['error'] is None,
                     'Room archived admin operation differs')
                need(case['archiveInactive'] is True and sha(row['payloadJson'].encode()) == case['archivedPayloadSha256'],
                     'Room payload SHA differs')
                if case['kind'] != 'CATALOG':
                    need(payload == packets[case['originalId']]['admin'][0]['payload'], 'Room admin payload changed')
        need(len(snapshot['online_closings']) == (1 if phase == 'lifecycle' else 0), 'unexpected local closing cache')
    final_room = snapshots['lifecycle']
    archived = {r['kind']: r for r in final_room['online_recovered_admin']}
    setting_case, create_case, update_case, catalog_case = all_cases[:4]
    need(setting_case['finalPayloadSha256'] == setting_case['sourcePayloadSha256'], 'settings intended payload changed')
    update_json = archived['TEAM_UPDATE']['payloadJson']
    expected_update_json = update_json[:-1] + ',"expectedRevision":' + str(update_case['expectedRevision']) + '}'
    need(sha(expected_update_json.encode()) == update_case['newOperationPayloadSha256'], 'normal membership payload SHA differs')
    need(sha(archived['CATALOG']['payloadJson'].encode()) == catalog_case['publishedPayloadSha256'], 'published catalog SHA differs from archive')
    need(create_case['passwordRequiredOutsideStoredDraft'] is True and create_case['passwordAbsentFromStoredPayload'] is True,
         'fresh password boundary not exercised')

    database = read(out / 'database-after.json'); no_secrets(database, True)
    need(database['internalErrorCount'] == 0 and database['fixturesRetained'] is True
         and database['entries'] == database['receipts'] == database['audit'] == [], 'untested default outlet was mutated')
    admin = read(out / 'admin-database-after.json'); no_secrets(admin, True)
    need(admin['outletId'] == outlet, 'PostgreSQL snapshot scope differs')
    expected_counts = {'outlets': 1, 'memberships': 5, 'members': 5, 'catalog_versions': 2, 'operation_receipts': 9,
                       'outlet_settings_audit': 1, 'membership_audit': 2, 'closings': 3, 'closing_audit': 8,
                       'closing_entries': 6, 'closing_reports': 1}
    need({k: len(admin[k]) for k in expected_counts} == expected_counts, 'PostgreSQL object/audit counts differ')
    receipts = {r['operation_id']: r for r in admin['operation_receipts']}
    need(len(receipts) == 9 and set(receipts) == {r['newId'] for r in all_cases}
         and not ({r['originalId'] for r in all_cases} & set(receipts))
         and all(r['outlet_id'] == outlet and r['actor_id'] == actor for r in receipts.values()), 'PostgreSQL receipt identity differs')
    run = installation['run']; expected_name = 'Pulihan outlet ' + run[:8]
    setting_payload = {'name': expected_name, 'timezone': 'Asia/Makassar', 'cutoffMinutes': 90}
    need(setting_case['expectedRevision'] == 0 and setting_case['finalRevision'] == 1
         and packets[setting_case['originalId']]['admin'][0]['payload'] == setting_payload, 'settings fixture revision/value differs')
    outlet_row = admin['outlets'][0]
    need(outlet_row['id'] == outlet and outlet_row['settings_revision'] == 1 and outlet_row['name'] == expected_name
         and outlet_row['timezone'] == 'Asia/Makassar' and outlet_row['cutoff_minutes'] == 90 and outlet_row['active'] is True,
         'settings database outcome differs')
    settings_audit = admin['outlet_settings_audit'][0]
    need(settings_audit['actor_id'] == actor and settings_audit['operation_id'] == setting_case['newId']
         and settings_audit['revision'] == 1 and settings_audit['after_value'] == setting_payload
         and settings_audit['before_value'] == {'name': fixture['adminOutletName'], 'timezone': 'Asia/Jakarta', 'cutoffMinutes': 0},
         'settings audit differs')
    need(receipts[setting_case['newId']]['response'] == {'outletId': outlet, 'operationId': setting_case['newId'],
                                                       'settingsRevision': 1, **setting_payload}, 'settings receipt differs')
    username = 'qa.recovered.' + run.replace('-', '')[:20]; display_name = 'Anggota pulihan ' + run[:8]
    created_id = identifier(create_case['createdAccountId']); need(update_case['targetAccountId'] == created_id, 'team target differs')
    need(packets[create_case['originalId']]['admin'][0]['payload'] == {'username': username, 'displayName': display_name, 'role': 'EMPLOYEE'}
         and packets[update_case['originalId']]['admin'][0]['payload'] == {'accountId': created_id, 'role': 'SUPERVISOR', 'active': False}
         and update_case['expectedRevision'] == 0 and update_case['finalRevision'] == 1, 'team source/revisions differ')
    members = {m['id']: m for m in admin['members']}
    need(set(members) == {actor, fixture['backupAdmin'], fixture['employee'], fixture['colleague'], created_id}, 'team member set changed')
    for account, role in [(actor, 'ADMIN'), (fixture['backupAdmin'], 'ADMIN'), (fixture['employee'], 'EMPLOYEE'), (fixture['colleague'], 'EMPLOYEE')]:
        member = members[account]
        need(member['role'] == role and member['active'] is True and member['member_active'] is True
             and int(member['revision']) == 0, 'existing fixture membership changed')
    member = members[created_id]
    need(member['username'] == username and member['display_name'] == display_name and member['role'] == 'SUPERVISOR'
         and member['active'] is True and member['member_active'] is False and int(member['revision']) == 1, 'created/updated member differs')
    memberships = {row['account_id']: row for row in admin['memberships']}
    need(set(memberships) == set(members), 'raw membership keys differ from public roster')
    for account, membership in memberships.items():
        need(membership['outlet_id'] == outlet and membership['role'] == members[account]['role']
             and membership['active'] == members[account]['member_active']
             and int(membership['revision']) == int(members[account]['revision']), 'raw membership differs from public roster')
    for case, kind, role, active, revision in [(create_case, 'CREATE_ACCOUNT', 'EMPLOYEE', True, 0),
                                               (update_case, 'UPDATE_MEMBERSHIP', 'SUPERVISOR', False, 1)]:
        response = receipts[case['newId']]['response']
        need(response == {'outletId': outlet, 'operationId': case['newId'], 'kind': kind, 'changed': True,
                          'member': {'id': created_id, 'username': username, 'displayName': display_name, 'role': role,
                                     'active': active, 'accountActive': True, 'revision': revision}}, 'team receipt differs')
    member_audits = {r['action']: r for r in admin['membership_audit']}
    need(set(member_audits) == {'CREATE_ACCOUNT', 'UPDATE_MEMBERSHIP'}
         and all(r['actor_id'] == actor and r['subject_id'] == created_id for r in member_audits.values()), 'membership audit actors differ')
    need(member_audits['CREATE_ACCOUNT']['before_value'] is None
         and member_audits['CREATE_ACCOUNT']['after_value'] == {'username': username, 'role': 'EMPLOYEE', 'active': True}
         and member_audits['UPDATE_MEMBERSHIP']['before_value'] == {'role': 'EMPLOYEE', 'active': True}
         and member_audits['UPDATE_MEMBERSHIP']['after_value'] == {'role': 'SUPERVISOR', 'active': False}, 'membership audit values differ')

    original_catalog = fixture['catalog']; source_catalog = copy.deepcopy(original_catalog)
    for menu in source_catalog['menus']:
        menu['name'] = 'Pulihan ' + menu['name']
    need(packets[catalog_case['originalId']]['admin'][0]['payload'] == source_catalog, 'catalog source model differs')
    published_catalog = copy.deepcopy(source_catalog); first_id = min(m['id'] for m in published_catalog['menus'])
    for menu in published_catalog['menus']:
        if menu['id'] == first_id:
            menu['name'] = 'Review ' + menu['name']
    need(catalog_case['editedMenuId'] == first_id and catalog_case['expectedRevision'] == 1
         and catalog_case['finalRevision'] == 2 and catalog_case['editableDraftPreserved'] is True
         and catalog_case['oldSnapshotUnchanged'] is True
         and json.loads(archived['CATALOG']['payloadJson']) == published_catalog, 'catalog edit/archive model differs')
    versions = sorted(admin['catalog_versions'], key=lambda r: r['revision'])
    need([r['revision'] for r in versions] == [1, 2] and [r['snapshot'] for r in versions] == [original_catalog, published_catalog]
         and all(r['created_by'] == actor for r in versions)
         and versions[0]['id'] == catalog_case['oldCatalogVersion'] and versions[1]['id'] == catalog_case['newCatalogVersion'], 'catalog versions differ')
    response = receipts[catalog_case['newId']]['response']
    need(response['versionId'] == versions[1]['id'] and response['revision'] == 2 and response['catalog'] == published_catalog,
         'catalog publication receipt differs')
    closings = {r['business_date']: r for r in admin['closings']}
    need(set(closings) == {'2026-07-06', '2026-07-07', '2026-07-08'}, 'closing fixture dates differ')
    for date, version, snapshot in [('2026-07-06', versions[0]['id'], original_catalog),
                                    ('2026-07-07', versions[1]['id'], published_catalog),
                                    ('2026-07-08', versions[1]['id'], published_catalog)]:
        closing = closings[date]
        need(closing['snapshot'] == snapshot and closing['catalog_version_id'] == version and closing['created_by'] == actor
             and closing['timezone'] == 'Asia/Makassar' and closing['cutoff_minutes'] == 90, 'frozen closing snapshot differs')
        need(closing['state'] == 'DRAFT' and closing['generation'] == (3 if date == '2026-07-08' else 1)
             and closing['revision'] == (5 if date == '2026-07-08' else 0), 'closing final state differs')
    need(closings['2026-07-06']['id'] == catalog_case['oldClosing'] and closings['2026-07-07']['id'] == catalog_case['newClosing'],
         'catalog old/new closing identifiers differ')
    closing_ids = {r['id'] for r in closings.values()}
    need(all(r['closing_id'] in closing_ids and r['quantity'] == 0 and int(r['revision']) == 0 and r['updated_by'] is None
             for r in admin['closing_entries']), 'unintended quantity mutation')
    need(collections.Counter(r['closing_id'] for r in admin['closing_entries']) == {key: 2 for key in closing_ids}, 'entry/menu count differs')
    expected_menu_ids = {row['id'] for row in published_catalog['menus']}
    need(all({row['menu_id'] for row in admin['closing_entries'] if row['closing_id'] == key} == expected_menu_ids
             for key in closing_ids), 'frozen closing entry menu IDs differ')
    lifecycle = all_cases[4:]; states = ['DRAFT', 'SUBMITTED', 'DRAFT', 'SUBMITTED', 'LOCKED', 'DRAFT']
    generations = [1, 1, 2, 2, 2, 3]; actions = ['submit', 'return', 'submit', 'lock', 'reopen']
    closing_id = closings['2026-07-08']['id']; audits = {r['operation_id']: r for r in admin['closing_audit'] if r['operation_id'] is not None}
    need(len(audits) == 5 and set(audits) == {r['newId'] for r in lifecycle}, 'lifecycle audit IDs differ')
    need(all(r['actor_id'] == actor and r['actor_username'] == fixture['adminUsername']
             and r['actor_name'] == fixture['adminDisplayName'] for r in admin['closing_audit']), 'closing audit actor differs')
    for index, (case, action) in enumerate(zip(lifecycle, actions)):
        need(case['closing'] == closing_id and case['action'] == action and case['beforeState'] == states[index]
             and case['afterState'] == states[index + 1] and case['beforeGeneration'] == generations[index]
             and case['afterGeneration'] == generations[index + 1]
             and case['beforeRevision'] == index and case['afterRevision'] == index + 1, 'lifecycle transition/revision differs')
        reason = 'Koreksi pulihan ' + action + ' ' + run[:8] if action in ['return', 'reopen'] else ''
        need(case['reason'] == reason, 'lifecycle reason differs')
        audit = audits[case['newId']]; response = receipts[case['newId']]['response']
        audit_reason = reason if action in ['return', 'reopen'] else None
        need(audit['closing_id'] == closing_id and audit['action'] == action.upper() and audit['reason'] == audit_reason
             and audit['generation'] == generations[index + 1] and audit['closing_revision'] == index + 1,
             'lifecycle audit state/reason differs')
        need(response['closingId'] == closing_id and response['state'] == states[index + 1]
             and response['generation'] == generations[index + 1] and response['closingRevision'] == index + 1
             and (response['report'] is None if action != 'lock' else response['report']['generation'] == 2),
             'lifecycle receipt outcome differs')
    creates = [r for r in admin['closing_audit'] if r['action'] == 'CREATE']
    need(len(creates) == 3 and {r['closing_id'] for r in creates} == closing_ids
         and all(r['operation_id'] is None and r['closing_revision'] == 0 and r['generation'] == 1 for r in creates), 'closing creation audits differ')
    report = admin['closing_reports'][0]; frozen_report = report['snapshot']; lock = lifecycle[3]
    need(report['closing_id'] == closing_id and report['generation'] == 2 and report['closing_revision'] == 4
         and report['locked_by'] == actor and frozen_report['format'] == 'closing-count-report'
         and frozen_report['closingId'] == closing_id and frozen_report['generation'] == 2 and frozen_report['closingRevision'] == 4
         and frozen_report['reviewedBy']['id'] == actor and frozen_report['closing']['state'] == 'LOCKED'
         and frozen_report['closing']['generation'] == 2 and frozen_report['closing']['closingRevision'] == 4
         and frozen_report['closing']['catalog'] == published_catalog
         and receipts[lock['newId']]['response']['report']['id'] == report['id'], 'immutable lock report differs')
    cached = final_room['online_closings'][0]; cached_payload = json.loads(cached['snapshotJson'])
    need(cached['scope'] == scope and cached['closingId'] == closing_id and cached['state'] == 'DRAFT'
         and cached['generation'] == 3 and cached['revision'] == 5 and cached_payload['catalog'] == published_catalog,
         'final local closing cache differs')
    need(len(final_room['online_entries']) == 2 and all(r['scope'] == scope and r['closingId'] == closing_id
         and r['generation'] == 3 and r['serverQuantity'] == r['localQuantity'] == r['serverRevision'] == 0 and r['status'] == 'SYNCED'
         for r in final_room['online_entries']), 'final local entry cache differs')
    need({r['menuId'] for r in final_room['online_entries']} == expected_menu_ids, 'local entry menu IDs differ')

    scenes = {'settings': ['review', 'held-after-recreation', 'confirmed', '1-handled'],
              'team': ['create-review', 'fresh-password-required', 'created', '2-handled', 'update-review', 'update-held', 'updated', '3-handled'],
              'catalog': ['review', 'file-recipes', 'server-recipes', 'editable-after-recreation', 'publish-review', 'published', '4-handled'],
              'lifecycle': [f'{i}-{action}-{suffix}' for i, action in enumerate(actions) for suffix in ['review', 'held', 'confirmed']]}
    scenes['lifecycle'] += [f'{n}-handled' for n in range(5, 10)]
    screenshots = []
    for phase, phase_scenes in scenes.items():
        for scene in phase_scenes:
            path = out / f'native-pending-admin-{phase}-{scene}.png'; body = path.read_bytes()
            need(body.startswith(b'\x89PNG\r\n\x1a\n'), 'native screen evidence missing')
            screenshots.append({'file': path.name, 'sha256': sha(body)})
    apks = read(out / 'apk-inputs.json')
    need(len(apks) == 2 and {Path(r['Path']).name for r in apks} == {'app-debug.apk', 'app-debug-androidTest.apk'}, 'APK pair differs')
    for record in apks:
        path = Path(record['Path']).resolve()
        need(path.is_relative_to((REPO / 'app/build/outputs/apk').resolve())
             and sha(path.read_bytes()).upper() == record['Hash'], 'APK binding changed')
    build = {'completedBuildLogProvided': False, 'jvmTestsClaimed': False, 'lintClaimed': False}
    if args.build_log is not None:
        build_text = log(args.build_log)
        need('BUILD SUCCESSFUL' in build_text and 'BUILD FAILED' not in build_text
             and all(':app:' + task in build_text for task in ['testDebugUnitTest', 'lintDebug', 'assembleDebug', 'assembleDebugAndroidTest']),
             'required completed build evidence missing')
        suites = [ET.parse(p).getroot().attrib for p in (REPO / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
        need(sum(int(r['tests']) for r in suites) == 25 and all(int(r['failures']) == int(r['errors']) == int(r['skipped']) == 0 for r in suites),
             'JVM result differs')
        lint = ET.parse(REPO / 'app/build/reports/lint-results-debug.xml').getroot()
        need(not any(r.attrib['severity'] in ['Error', 'Fatal'] for r in lint), 'lint errors present')
        build = {'completedBuildLogProvided': True, 'buildLogSha256': sha(args.build_log.read_bytes()),
                 'jvmTestsClaimed': True, 'jvmTests': 25, 'lintClaimed': True, 'lintErrors': 0,
                 'lintWarnings': sum(r.attrib['severity'] == 'Warning' for r in lint)}
    sanitized = {'scope': scope, 'roomSchema': 10, 'legacyRowsSha256': final_room['legacyRowsSha256'],
                 'admin': [{k: row[k] for k in ['scope', 'originalOperationId', 'fingerprint', 'group', 'kind', 'operationId',
                           'expectedRevision', 'status', 'wasSent', 'active']} | {'payload': json.loads(row['payloadJson']),
                           'payloadSha256': sha(row['payloadJson'].encode())} for row in final_room['online_recovered_admin']],
                 'lifecycleClaims': final_room['online_recovery_claims'],
                 'localClosing': {k: cached[k] for k in ['closingId', 'generation', 'revision', 'state']},
                 'localEntries': final_room['online_entries'], 'postgresCounts': expected_counts,
                 'postgresReceiptIds': sorted(receipts), 'sourceOperationIdsNeverRecorded': [r['originalId'] for r in all_cases]}
    no_secrets(sanitized)
    result = {'passed': True, 'nativeNormalTests': 4, 'recoveryCases': 9, 'caseKinds': KINDS,
              'cumulativeInboxCounts': COUNTS, 'sameEmulator': True, 'isolatedSecondaryUser': installation['user'],
              'appUid': proofs[0]['uid'], 'processes': [p['pid'] for p in proofs],
              'independentAcceptedPacketAndPayloadHashesMatched': True, 'independentFrozenRoomProvenanceMatched': True,
              'independentPostgresReceiptsAndAuditMatched': True, 'adminReceipts': 9,
              'newOperationIdsUsed': True, 'originalOperationIdsNeverRecorded': True,
              'heldThroughNativeContinuationAndActivityRecreation': True, 'allFinalSendsUsedProductionUi': True,
              'duplicateActionsBlocked': True, 'freshPasswordOutsideStoredDraft': True,
              'catalogEditableAndHistoricalSnapshotsPreserved': True,
              'lifecycleActions': actions, 'finalLifecycleState': 'DRAFT', 'finalLifecycleGeneration': 3, 'finalLifecycleRevision': 5,
              'defaultOutletClosingEntriesReceiptsAndQuantityAuditUnchanged': True, 'legacyLogicalHashesAndFrozenRowsUnchanged': True,
              'primaryRoomFilesUnchanged': True, 'deviceSettingsUsersAndCurrentUserRestored': True,
              'packetsSeededDirectlyIntoInbox': True, 'documentsPickerExercised': False, 'physicalPhoneAccessed': False,
              'frozenSecondaryUidQuiescentAfterNormalTeardown': True, 'liveProcessStopClaim': False,
              'observedConfigurations': {'settings': 'light/100%', 'team': 'dark/200%', 'catalog': 'light/100%', 'lifecycle': 'light/100%'},
              'screenshots': screenshots, 'build': build, 'completePt07Acceptance': False,
              'remaining': ['Human/model visual inspection of native admin dark/large screens', 'Remaining PT07/PT08 acceptance review',
                            'CCS08 complete local demo package', 'Final physical-device UAT and later public deployment acceptance']}
    (out / 'packet-index.json').write_text(json.dumps(packet_index, indent=2) + '\n', encoding='utf-8')
    (out / 'sanitized-provenance.json').write_text(json.dumps(sanitized, indent=2) + '\n', encoding='utf-8')
    (out / 'runtime-proof.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: result[k] for k in ['passed', 'nativeNormalTests', 'recoveryCases', 'adminReceipts',
                                           'independentFrozenRoomProvenanceMatched', 'independentPostgresReceiptsAndAuditMatched',
                                           'completePt07Acceptance']}))


if __name__ == '__main__':
    main()
