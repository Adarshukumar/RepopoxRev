import json, urllib.request, hashlib, zipfile, io, pathlib, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parent.parent
TAG='2026.1103.1743'
def get(url):
    req=urllib.request.Request(url,headers={'User-Agent':'Frame-build','Accept':'application/vnd.github+json'})
    return urllib.request.urlopen(req,timeout=120).read()
release=json.loads(get(f'https://api.github.com/repos/uBlockOrigin/uBOL-home/releases/tags/{TAG}'))
asset=next(a for a in release['assets'] if a['name'].endswith('.firefox.signed.xpi'))
blob=get(asset['browser_download_url']); sha=hashlib.sha256(blob).hexdigest()
assert sha == 'cca96aed8355bb59168c4af3da638a92dda18378d286af996fbf2013f45d7cc7'
assert asset.get('digest') == 'sha256:'+sha, 'Upstream asset digest is absent or mismatched'
(root/'upstream').mkdir(exist_ok=True)
(root/'upstream'/asset['name']).write_bytes(blob)
with zipfile.ZipFile(io.BytesIO(blob)) as z:
    for n in z.namelist():
        assert not n.startswith('/') and '..' not in pathlib.PurePosixPath(n).parts
    z.extractall(root/'app/src/main/assets/ubol')
manifest=json.loads((root/'app/src/main/assets/ubol/manifest.json').read_text())
id=manifest['browser_specific_settings']['gecko']['id'];(root/'ubol-id.txt').write_text(id)
metadata=ET.fromstring(get('https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml'))
version='159.0.20261102144711'
assert version in [v.text for v in metadata.findall('versioning/versions/version')], 'Pinned engine unavailable'
assert version and 'a1' not in version and 'b' not in version, version
(root/'geckoview-version.txt').write_text(version)
# Choose a real enabled upstream generic blocking rule for a local network control experiment.
# This never modifies the extension or injects custom rules.
candidates=[]
for rset in manifest['declarative_net_request']['rule_resources']:
    if not rset['enabled']: continue
    for r in json.loads((root/'app/src/main/assets/ubol'/rset['path'].lstrip('/')).read_text()):
        c=r['condition']; f=c.get('urlFilter','')
        if r['action']['type']!='block' or not f.startswith('/') or not f.endswith('.js'): continue
        if any(x in c for x in ['requestDomains','initiatorDomains','domainType','excludedRequestDomains','excludedInitiatorDomains']):continue
        if any(x in f for x in ['*','^','|','?']):continue
        if 'resourceTypes' in c and 'script' not in c['resourceTypes']:continue
        if 'script' in c.get('excludedResourceTypes',[]):continue
        candidates.append((f,rset['id'],r['id']))
assert candidates, 'No suitable unmodified upstream rule for blocking test'
path, ruleset, rule=candidates[0]
probe={'path':path,'ruleset':ruleset,'ruleId':rule}
assets=root/'app/src/main/assets';assets.mkdir(exist_ok=True)
(assets/'probe.json').write_text(json.dumps(probe))
report={'ubol_tag':TAG,'ubol_id':id,'ubol_sha256':sha,'geckoview':version,'probe':probe,'manifest_version':manifest['manifest_version']}
(root/'build-manifest.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
