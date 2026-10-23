import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
const runName=process.argv[2]||'http';if(!/^[a-z0-9-]{1,40}$/.test(runName))throw Error('Invalid owned QA run name');
const secret=JSON.parse(readFileSync(path.join(process.env.LOCALAPPDATA,'PortfolioQA/secrets/closing-count-local-r1.json'),'utf8'));
const run=spawnSync(process.execPath,['test/android-http-fixture.mjs'],{cwd:'D:/Code/Worktrees/Closing-Count-Portfolio/service',stdio:'inherit',env:{...process.env,
 CCS_ENVIRONMENT:'QA',CCS_INSTANCE:secret.instance,CCS_DATABASE_URL:`postgresql://ccs_portfolio_app:${secret.appPassword}@127.0.0.1:55436/closing_count_portfolio_qa`,
 CCS_HTTP_EVIDENCE_DIR:`D:/Code/Documents/Career-Portfolio-2026/evidence/closing-count-layout-r1/${runName}/fixture`,
 CCS_HTTP_FIXTURE_FILE:'D:/Code/ToolCache/PortfolioQA/ccs-http-r1-fixture.json'}});
process.exitCode=run.status??1;
