"""Compile the headless card bridge with cached jars; no backend or simulator required."""
from pathlib import Path
import os, subprocess, shutil, json, datetime
from verification_evidence import lock, source_hash, clean_classes, publish
root=Path(__file__).resolve().parents[1]
cache=root/'.cache/offline';cache.mkdir(parents=True,exist_ok=True)
guard=lock(cache/'verification.lock')
java=Path(os.environ.get('JAVA_HOME',''))/'bin'
if not (java/'javac.exe').exists():java=Path(r'C:\Program Files\Java\jdk-21.0.11\bin')
libs=root/'target/dependency';libs.mkdir(parents=True,exist_ok=True)
jars=[]
for artifact in ['jackson-annotations','jackson-core','jackson-databind']:
 candidates=list(libs.glob(artifact+'-*.jar'))
 if not candidates:candidates=list((Path(os.environ['USERPROFILE'])/'.m2/repository/com/fasterxml/jackson/core'/artifact).rglob('*.jar'))
 candidates=[p for p in candidates if '-sources' not in p.name and '-javadoc' not in p.name]
 if not candidates:raise SystemExit('Missing cached dependency: '+artifact+'. Run Maven build once with internet access.')
 jar=sorted(candidates,key=lambda p:p.name)[-1]
 if jar.parent!=libs:shutil.copy2(jar,libs/jar.name);jar=libs/jar.name
 jars.append(jar)
classpath=';'.join(map(str,jars))
def call(args):subprocess.run([str(a) for a in args],cwd=root,check=True)
def compile_sources(files,out,cp,name):
 clean_classes(root,out);arguments=cache/(name+'.args')
 lines=['--release','21','-proc:none','-encoding','UTF-8','-classpath','"'+cp.replace(chr(92),'/')+'"','-d','"'+str(out).replace(chr(92),'/')+'"']
 lines+=['"'+str(p).replace(chr(92),'/')+'"' for p in files]
 arguments.write_text(chr(10).join(lines),encoding='utf-8')
 call([java/'javac.exe','@'+str(arguments)])
main=root/'target/classes'
compile_sources(list((root/'src/main/java').rglob('*.java')),main,classpath,'main')
tests=root/'target/test-classes'
compile_sources([p for p in (root/'src/test/java').rglob('*.java') if not p.name.endswith('Test.java')],tests,classpath+';'+str(main),'tests')
call([java/'java.exe','-Djava.awt.headless=true','-cp',classpath+';'+str(main)+';'+str(tests),'vn.kma.desktop.FrontendApiChecks'])
(cache/'classpath.txt').write_text(classpath,encoding='utf-8')
report={'recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceSha256':source_hash(root),'mode':'cached-headless-bridge','bridgeCompile':'PASS','javaApiChecks':'PASS','reactBuild':'separate npm run build','oracleSimulator':'not-tested'}
(root/'target/test-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
publish(root,'web-bridge-latest.json',report)
print('Headless card bridge compiled; Java API checks passed. Run npm test and npm run build for React.')
