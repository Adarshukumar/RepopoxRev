import sys, pathlib, zipfile, hashlib
root=pathlib.Path('app/src/main/assets/ubol')
with zipfile.ZipFile(sys.argv[1]) as apk:
    checked=0
    for path in root.rglob('*'):
        if not path.is_file(): continue
        name='assets/ubol/'+path.relative_to(root).as_posix()
        assert name in apk.namelist(), f'Upstream extension file omitted from APK: {name}'
        assert hashlib.sha256(apk.read(name)).digest()==hashlib.sha256(path.read_bytes()).digest(),f'Modified extension asset: {name}'
        checked+=1
    assert checked>100
    print(f'PASS: {checked} upstream extension files preserved byte-for-byte in APK')
