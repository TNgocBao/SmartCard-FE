"""Optional FE–BE contract check using a built backend artifact, never a production source dependency."""
from pathlib import Path
import argparse,os,subprocess,json,hashlib
from verification_evidence import lock,source_hash,clean_classes,publish
parser=argparse.ArgumentParser();parser.add_argument('--backend',required=True,type=Path);args=parser.parse_args()
root=Path(__file__).resolve().parents[1];backend=args.backend.resolve()
verification_lock=lock(root/'.cache/offline/verification.lock')
frontend_before=source_hash(root);backend_before=source_hash(backend)
for directory in [root,backend]:
 report=json.loads((directory/'target/test-results.json').read_text(encoding='utf-8'))
 if report.get('sourceSha256')!=source_hash(directory):raise SystemExit('Source changed since its standalone verification; rebuild '+str(directory))
cp_file=backend/'.cache/offline/classpath.txt'
if not cp_file.is_file():raise SystemExit('Build/test the backend with its offline_verify.py first to prepare the verification runtime')
if not (root/'target/classes/vn/kma/desktop/api/IdpApi.class').exists():raise SystemExit('Build frontend first')
java=Path(os.environ.get('JAVA_HOME',r'C:\Program Files\Java\jdk-21.0.11'))/'bin'
if not java.exists():java=Path(r'C:\Program Files\Java\jdk-21.0.11\bin')
out=root/'target/integration-classes';clean_classes(root,out)
classpath=cp_file.read_text(encoding='utf-8')+';'+str(root/'target/classes')+';'+str(backend/'target/classes')+';'+str(backend/'target/test-classes')
argfile=root/'.cache/offline/integration.args';argfile.parent.mkdir(parents=True,exist_ok=True)
argfile.write_text('--release\n21\n-proc:none\n-encoding\nUTF-8\n-classpath\n"'+classpath.replace('\\','/')+'"\n-d\n"'+str(out).replace('\\','/')+'"\n"'+str(root/'integration-tests/ContractFlowChecks.java').replace('\\','/')+'"',encoding='utf-8')
subprocess.run([str(java/'javac.exe'),'@'+str(argfile)],cwd=root,check=True)
verification_env=os.environ.copy()
verification_env.update({'OTEL_SDK_DISABLED':'true','DOTNET_CLI_TELEMETRY_OPTOUT':'1','POWERSHELL_TELEMETRY_OPTOUT':'1'})
result=subprocess.run([str(java/'java.exe'),'-Djava.security.manager=allow','-Dorg.springframework.boot.logging.LoggingSystem=none','-cp',classpath+';'+str(out),'vn.kma.desktop.integration.ContractFlowChecks'],cwd=root,env=verification_env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
(root/'target/e2e-verification.log').write_text(result.stdout,encoding='utf-8');print('\n'.join(result.stdout.splitlines()[-30:]))
if result.returncode or 'FE-BE CONTRACT CHECKS PASSED:' not in result.stdout:raise SystemExit(result.returncode or 1)
print('Optional real-code FE/BE contract simulation passed. Native simulator and network deployment remain separate checks.')

report_file=root/'target/e2e-test-results.json'
report=json.loads(report_file.read_text(encoding='utf-8'))
if frontend_before!=source_hash(root) or backend_before!=source_hash(backend):raise SystemExit('Sources changed during integration verification; rerun builds and tests.')
report['frontendSourceSha256']=frontend_before;report['backendSourceSha256']=backend_before
publish(root,'fe-be-contract-latest.json',report)
report_file.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
# Integration evidence is independent from the React UI checks.
