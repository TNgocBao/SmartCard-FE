"""Helpers for repeatable verification; this file is kept inside each independent repository."""
from pathlib import Path
import atexit,hashlib,json,shutil

def lock(path):
    path.parent.mkdir(parents=True,exist_ok=True)
    handle=path.open('a+b')
    if path.stat().st_size==0:handle.write(b'0');handle.flush()
    handle.seek(0)
    try:
        import msvcrt
        msvcrt.locking(handle.fileno(),msvcrt.LK_NBLCK,1)
    except OSError:
        handle.close();raise SystemExit('Verification already running for this repository; wait for its result.')
    atexit.register(handle.close)
    return handle

def source_hash(root):
    digest=hashlib.sha256()
    files=[p for folder in ['src','integration-tests'] for p in (root/folder).rglob('*') if p.is_file()]
    files.extend(p for p in [root/'pom.xml',root/'package.json',root/'package-lock.json',root/'vite.config.js',root/'index.html'] if p.is_file())
    for file in sorted(files):
        digest.update(file.relative_to(root).as_posix().encode());digest.update(b'\0');digest.update(file.read_bytes());digest.update(b'\0')
    return digest.hexdigest()

def clean_classes(root,out):
    target=(root/'target').resolve();resolved=out.resolve()
    if not resolved.is_relative_to(target) or resolved.name not in {'classes','test-classes','integration-classes'}:
        raise SystemExit('Refusing cleanup outside known generated class directories')
    if out.exists():shutil.rmtree(out)
    out.mkdir(parents=True,exist_ok=True)

def publish(root,name,report):
    destination=root/'docs/test-evidence';destination.mkdir(parents=True,exist_ok=True)
    (destination/name).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
