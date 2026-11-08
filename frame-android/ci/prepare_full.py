"""Experimental full uBO build; never alters the Lite package or upstream files."""
import hashlib, io, json, pathlib, re, shutil, urllib.request, zipfile
root=pathlib.Path(__file__).resolve().parent.parent
TAG='1.75.4'
SHA='e2df503cce7cf5069130618c349695f62bdddb9eb8c189156d027269ae4ae267'
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Frame-experiment'}),timeout=120).read()
r=json.loads(get(f'https://api.github.com/repos/gorhill/uBlock/releases/tags/{TAG}'))
a=next(a for a in r['assets'] if a['name'].endswith('.firefox.signed.xpi'))
blob=get(a['browser_download_url'])
assert hashlib.sha256(blob).hexdigest()==SHA
assert a['digest']=='sha256:'+SHA
(root/'upstream').mkdir(exist_ok=True)
(root/'upstream'/a['name']).write_bytes(blob)
dest=root/'app/src/main/assets/ubol'
if dest.exists(): shutil.rmtree(dest)
with zipfile.ZipFile(io.BytesIO(blob)) as z:
    assert all(not n.startswith('/') and '..' not in pathlib.PurePosixPath(n).parts for n in z.namelist())
    z.extractall(dest)
m=json.loads((dest/'manifest.json').read_text())
ident=(m.get('browser_specific_settings') or m['applications'])['gecko']['id']
assert ident=='uBlock0@raymondhill.net'
(root/'ubol-id.txt').write_text(ident)
(root/'geckoview-version.txt').write_text('159.0.20261102144711')
# Pick a literal generic script rule from the actual bundled default uBlock list.
# No custom rules, replacement engine, or changes to the extension.
candidates=[]
for p in sorted(dest.rglob('*.txt')):
    if 'ublock' not in str(p.relative_to(dest)) or not p.name.startswith('filters'): continue
    for line in p.read_text(errors='replace').splitlines():
        if re.fullmatch(r'/[\w./-]+\.js',line): candidates.append((line,p.relative_to(dest).as_posix()))
assert candidates, 'No fixture rule found in bundled defaults: '+str([str(p.relative_to(dest)) for p in dest.rglob('*.txt')])
path,source=candidates[0]
probe={'path':path,'source':source,'rule':path}
(root/'app/src/main/assets/probe.json').write_text(json.dumps(probe))
report={'extension':'uBlock Origin (full)','version':TAG,'id':ident,'sha256':SHA,'manifest_version':m['manifest_version'],'probe':probe,'geckoview':'159.0.20261102144711'}
(root/'build-manifest.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
