"""Publish only a complete, verified RC14 build set produced from this exact commit."""
import hashlib,io,json,os,pathlib,time,urllib.request,urllib.error,urllib.parse,zipfile

repo=os.environ['GITHUB_REPOSITORY'];sha=os.environ['GITHUB_SHA'];token=os.environ['GH_TOKEN']
api='https://api.github.com/repos/'+repo
headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'Telares-RC14-CI'}
def request(url,body=None,method=None,content_type='application/json'):
    data=json.dumps(body).encode() if isinstance(body,dict) else body
    req=urllib.request.Request(url,data=data,headers=dict(headers,**{'Content-Type':content_type}),method=method)
    with urllib.request.urlopen(req,timeout=180) as response:
        result=response.read()
    return json.loads(result) if result else {}
required={
 'shock-tv-products.yml':6,
 'shock-tv-legacy-ios.yml':2,
 'telares-windows-play.yml':2,
}
deadline=time.monotonic()+50*60
while True:
    runs=[];pending=False
    for workflow in required:
        data=request(api+'/actions/workflows/'+workflow+'/runs?head_sha='+sha+'&event=push&per_page=20')
        matches=[r for r in data['workflow_runs'] if r['head_sha']==sha and r['head_branch']=='telares-release-candidate']
        if not matches:pending=True;continue
        run=max(matches,key=lambda r:r['id']);runs.append(run)
        if run['status']!='completed':pending=True
        elif run['conclusion']!='success':raise RuntimeError('Build failed: '+run['html_url'])
    if len(runs)==3 and not pending:break
    if time.monotonic()>deadline:raise TimeoutError('Build set incomplete for '+sha)
    print('Waiting for all three workflows at '+sha,flush=True);time.sleep(20)

root=pathlib.Path('dist/rc14');root.mkdir(parents=True,exist_ok=True)
records=[]
def nested_check(data,depth=0):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        bad=archive.testzip()
        if bad:raise ValueError('ZIP CRC mismatch: '+bad)
        if depth<3:
            for item in archive.infolist():
                if item.filename.lower().endswith(('.zip','.ipa','.apk','.jar')):
                    content=archive.read(item)
                    if zipfile.is_zipfile(io.BytesIO(content)):nested_check(content,depth+1)
for run in runs:
    artifacts=request(api+'/actions/runs/'+str(run['id'])+'/artifacts?per_page=100')['artifacts']
    workflow=run['path'].split('/')[-1]
    if len(artifacts)<required[workflow]:raise ValueError('Missing artifacts: '+workflow)
    for artifact in artifacts:
        name=artifact['name']
        if name.endswith('-Reports'):continue
        if 'rc14' not in name.lower() or artifact['expired']:raise ValueError('Non-RC14 or expired artifact: '+name)
        if artifact['workflow_run']['head_sha']!=sha:raise ValueError('Wrong artifact commit')
        # GitHub's artifact API redirects to short-lived object storage. Do not forward
        # Authorization to the different host serving the archive.
        class Redirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self,req,fp,code,msg,hdrs,newurl):
                return urllib.request.Request(newurl,headers={'User-Agent':'Telares-RC14-CI'})
        opener=urllib.request.build_opener(Redirect())
        with opener.open(urllib.request.Request(artifact['archive_download_url'],headers=headers),timeout=180) as response:data=response.read()
        digest=hashlib.sha256(data).hexdigest()
        if len(data)!=artifact['size_in_bytes']:raise ValueError('Size mismatch: '+name)
        if artifact.get('digest')!='sha256:'+digest:raise ValueError('Digest mismatch: '+name)
        nested_check(data)
        path=root/(name+'.zip');path.write_bytes(data)
        records.append({'name':name,'artifact_id':artifact['id'],'path':path.name,'size_bytes':len(data),'sha256':digest,'run_url':run['html_url'],'artifact_url':'https://github.com/'+repo+'/actions/runs/'+str(run['id'])+'/artifacts/'+str(artifact['id']),'commit':sha})
        print('Verified '+name,flush=True)
if len(records)<10:raise ValueError('Incomplete release')
manifest={'release':'Telares RC14','repository':repo,'commit':sha,'validation':'CI compilation, tests and archive CRC/SHA256; physical playback unverified','versions':json.loads(pathlib.Path('shock-tv-hub/design/release.json').read_text())['streamingTargets'],'artifacts':records}
(root/'manifesto.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
(root/'SHA256SUMS.txt').write_text(''.join(a['sha256']+'  '+a['path']+'\n' for a in records),encoding='utf-8')
notes=pathlib.Path('shock-tv-hub/design/RC14.md').read_text(encoding='utf-8')
(root/'README.md').write_text(notes+'\n\nCommit: '+sha+'\n',encoding='utf-8')
complete=pathlib.Path('dist/Telares-RC14-Complete-Bundle.zip')
with zipfile.ZipFile(complete,'w',zipfile.ZIP_STORED) as archive:
    for path in sorted(root.iterdir()):archive.write(path,'Telares-RC14/'+path.name)
with zipfile.ZipFile(complete) as archive:
    if archive.testzip():raise ValueError('Final bundle CRC mismatch')
checksum=hashlib.sha256(complete.read_bytes()).hexdigest()
complete.with_suffix('.zip.sha256').write_text(checksum+'  '+complete.name+'\n')
tag='telares-rc14-'+sha[:8]
payload={'tag_name':tag,'target_commitish':sha,'name':'Telares RC14 · '+sha[:8],'draft':True,'prerelease':True,'body':notes+'\n\nCommit: `'+sha+'`\n\n'+ '\n'.join('- '+r['html_url'] for r in runs)}
existing=[r for r in request(api+'/releases?per_page=100') if r['tag_name']==tag]
if existing:
    release=existing[0]
    if not release['draft']:raise RuntimeError('This exact-commit release is already published: '+release['html_url'])
else:release=request(api+'/releases',payload)
upload=release['upload_url'].split('{',1)[0]
for path in [*sorted(root.iterdir()),complete,complete.with_suffix('.zip.sha256')]:
    for asset in release.get('assets',[]):
        if asset['name']==path.name:request(api+'/releases/assets/'+str(asset['id']),method='DELETE')
    request(upload+'?name='+urllib.parse.quote(path.name),path.read_bytes(),'POST','application/zip' if path.suffix=='.zip' else 'application/octet-stream')
published=request(api+'/releases/'+str(release['id']),{'draft':False},'PATCH')
print('PUBLISHED '+published['html_url'],flush=True)
with open(os.environ['GITHUB_STEP_SUMMARY'],'a',encoding='utf-8') as summary:summary.write('[Telares RC14]('+published['html_url']+')\n\nCommit: '+sha+'\n\nBundle SHA256: `'+checksum+'`\n')
