export function configFromEnvironment(env = process.env) {
  // Separate fixed local targets: DEMO never adopts the laptop QA database.
  const demo = env.CCS_ENVIRONMENT === 'DEMO';
  const refusal = () => { throw new Error(`Closing Count requires the explicit owned ${demo ? 'DEMO' : 'QA'} target`); };
  if (!['QA','DEMO'].includes(env.CCS_ENVIRONMENT) || !/^[a-f0-9]{48}$/.test(env.CCS_INSTANCE || '')) refusal();
  let target;
  try { target = new URL(env.CCS_DATABASE_URL); } catch { refusal(); }
  if (target.protocol !== 'postgresql:' || target.hostname !== '127.0.0.1' || target.port !== (demo ? '55437' : '55436')
    || target.username !== 'ccs_portfolio_app' || !/^[a-f0-9]{64}$/.test(target.password)
    || target.pathname !== (demo ? '/closing_count_portfolio_demo' : '/closing_count_portfolio_qa') || target.search || target.hash) refusal();
  const port = Number(env.CCS_PORT || (demo ? 4372 : 4370));
  if (!Number.isInteger(port) || port < 1024 || port > 65535) refusal();
  return { environment: env.CCS_ENVIRONMENT, databaseUrl: target.href, instance: env.CCS_INSTANCE, host: '127.0.0.1', port };
}

export async function assertOwnedDatabase(client, config) {
  // Revalidate the complete profile before even issuing an identity query.
  const checked=configFromEnvironment({CCS_ENVIRONMENT:config.environment,CCS_INSTANCE:config.instance,CCS_DATABASE_URL:config.databaseUrl,CCS_PORT:String(config.port)});
  const { rows: [identity] } = await client.query(`SELECT current_database() AS database, current_user AS role,
    current_setting('server_version_num')::int AS version, r.rolsuper, r.rolcreatedb, r.rolcreaterole,
    d.datdba = r.oid AS owns_database FROM pg_roles r JOIN pg_database d ON d.datname=current_database()
    WHERE r.rolname=current_user`);
  if (identity?.database !== (checked.environment==='DEMO'?'closing_count_portfolio_demo':'closing_count_portfolio_qa') || identity.role !== 'ccs_portfolio_app'
    || identity.rolsuper || identity.rolcreatedb || identity.rolcreaterole || !identity.owns_database
    || identity.version < 160000 || identity.version >= 170000) throw new Error('Refusing unexpected QA database identity');
  const marker = await client.query('SELECT instance FROM public.ccs_instance');
  if (marker.rowCount !== 1 || marker.rows[0].instance !== config.instance) throw new Error('Refusing unowned QA database');
  return { database:identity.database, role:identity.role, restricted:true, instanceMatched:true };
}
