"""Copy existing Maven artifacts to a writable cache; recover original embedded POMs.

No downloads, version substitution, generated dependency descriptors, or source-cache writes.
"""
from pathlib import Path
import argparse, os, shutil, zipfile, json

parser=argparse.ArgumentParser()
parser.add_argument('--destination',type=Path,required=True)
args=parser.parse_args()
source=(Path(os.environ['USERPROFILE'])/'.m2/repository').resolve()
destination=args.destination.resolve()
workspace=Path(__file__).resolve().parents[2]
if not destination.is_relative_to(workspace) or destination==workspace:
    raise SystemExit('Destination must be a cache directory inside this project workspace.')
copied=recovered=0
for file in source.rglob('*'):
    if not file.is_file() or file.suffix not in {'.jar','.pom'}:continue
    target=destination/file.relative_to(source)
    if not target.exists():
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(file,target);copied+=1
    if file.suffix!='.jar' or file.stem.endswith(('-sources','-javadoc')):continue
    pom=target.with_suffix('.pom')
    if pom.exists():continue
    relative=file.relative_to(source)
    group='.'.join(relative.parts[:-3]);artifact=relative.parts[-3]
    embedded=f'META-INF/maven/{group}/{artifact}/pom.xml'
    try:
        with zipfile.ZipFile(file) as archive:
            if embedded in archive.namelist():
                pom.write_bytes(archive.read(embedded));recovered+=1
    except zipfile.BadZipFile:raise SystemExit('Invalid cached JAR: '+str(relative))
print(json.dumps({'artifactsCopied':copied,'originalEmbeddedPomsRecovered':recovered,'destination':str(destination)}))
