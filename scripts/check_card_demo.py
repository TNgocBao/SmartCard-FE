"""Test production APDU emulator and its real Spring backend contract, using an isolated H2 DB."""
from pathlib import Path
import os, subprocess, json, datetime, re, shutil
from verification_evidence import source_hash

root=Path(__file__).resolve().parents[1]
backend=root.parent/'backend'
java=Path(r'C:/Program Files/Java/jdk-21.0.11/bin')
runtime=backend/'target/cached-runtime'
if not (runtime/'classpath.txt').exists():
    raise SystemExit('Compile backend first: python backend/scripts/prepare_cached_runtime.py')
compilation=backend/'docs/test-evidence/cached-runtime-compilation-latest.json'
if not compilation.exists() or json.loads(compilation.read_text())['sourceSha256']!=source_hash(backend):
    raise SystemExit('Backend source changed. Run python backend/scripts/prepare_cached_runtime.py first.')
classpath=(runtime/'classpath.txt').read_text().strip()+';'+str(root/'target/classes')+';'+str(root/'target/dependency/*')+';'+str(backend/'src/test/resources')
out=root/'target/card-demo-checks';out.mkdir(parents=True,exist_ok=True)
sources=[root/'src/test/java/vn/kma/desktop/DemoCardChecks.java',root/'src/test/java/vn/kma/desktop/BridgeCardChecks.java',root/'integration-tests/ContractFlowChecks.java',backend/'src/test/java/vn/kma/idp/NetworkIsolation.java']
argfile=root/'target/card-demo-checks.args'
argfile.write_text('\n'.join(['--release','21','-proc:none','-parameters','-encoding','UTF-8','-classpath','"'+classpath.replace('\\','/')+'"','-d','"'+out.as_posix()+'"']+['"'+p.as_posix()+'"' for p in sources]),encoding='utf-8')
subprocess.run([str(java/'javac.exe'),'@'+str(argfile)],cwd=root,check=True)
env=dict(os.environ,OTEL_SDK_DISABLED='true')
reports=[]
for name,enabled in [('vn.kma.desktop.DemoCardChecks','false'),('vn.kma.desktop.BridgeCardChecks','false'),('vn.kma.desktop.BridgeCardChecks','true'),('vn.kma.desktop.integration.ContractFlowChecks','false')]:
    env['IDP_DEMO_CARD']=enabled
    run_args=root/'target/card-demo-runtime.args'
    run_args.write_text('\n'.join(['-Djava.security.manager=allow','-Dorg.springframework.boot.logging.LoggingSystem=none','-cp','"'+(str(out)+';'+classpath).replace('\\','/')+'"',name]),encoding='utf-8')
    result=subprocess.run([str(java/'java.exe'),'@'+str(run_args)],cwd=root,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
    log=root/'target'/('card-demo-'+name.split('.')[-1]+'-'+enabled+'.log');log.write_text(result.stdout,encoding='utf-8')
    print('\n'.join(result.stdout.splitlines()[-14:]))
    if result.returncode:raise SystemExit(result.returncode)
    count=int(re.findall(r'CHECKS PASSED: (\d+)',result.stdout)[-1])
    reports.append({'class':name,'demoEnabled':enabled,'passed':True,'checksPassed':count})
report={'recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'status':'PASS','frontendSourceSha256':source_hash(root),'backendSourceSha256':source_hash(backend),'checks':reports,'totalChecksPassed':sum(r['checksPassed'] for r in reports),'scope':'Production software APDU reader + CardClient + Java API client + real Spring backend/H2. Handler tests use in-memory HttpExchange. Not Oracle, physical NFC, MySQL, actual HTTP or browser interactions.'}
destination=root/'docs/test-evidence';destination.mkdir(parents=True,exist_ok=True)
(destination/'card-demo-latest.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
shutil.copy2(root/'target/e2e-test-results.json',destination/'card-demo-contract-latest.json')
print('CARD DEMO VERIFICATION PASSED')
