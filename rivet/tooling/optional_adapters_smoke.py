"""Live optional-adapter regression with supplied mod JARs and an isolated PostgreSQL container."""
import argparse,os,sys,secrets,subprocess,time,shutil,json,uuid,hashlib
from pathlib import Path
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('work',type=Path)
parser.add_argument('--mods',required=True,type=Path,help='Seven integration mods plus Create and Balm; copied only into the test profiles')
parser.add_argument('--database-port',type=int,default=55453)
parser.add_argument('--game-port',type=int,default=25592)
parser.add_argument('--scenario',action='append',choices=['core','access','waystones','access-server'])
args=parser.parse_args()
root=Path(__file__).resolve().parents[2];sys.path.insert(0,str(root/'rivet/tooling'))
from map_network import prepare,command,verify,stop
work=args.work.resolve();work.mkdir(parents=True,exist_ok=True)
address='127.0.0.1:'+str(args.game_port)
init=prepare(root,work,args.database_port,'rivet_test',args.game_port)
with init.open('a') as f:f.write("\ngradle.projectsEvaluated { gradle.rootProject.project(':mod').neoForge.mods { rivet_compat_tests { sourceSet gradle.rootProject.project(':mod').sourceSets.compatibilityFixtures } } }\n")
for side in ['server','client']:
 (work/side/'mods').mkdir(exist_ok=True)
 for jar in args.mods.glob('*.jar'):
  if side=='client' and jar.name.startswith('LuckPerms'):continue
  shutil.copyfile(jar,work/side/'mods'/jar.name)
(work/'server/config/spark').mkdir(exist_ok=True)
(work/'server/config/spark/config.json').write_text('{"backgroundProfiler":false}')
id=str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:Tester').digest(),version=3));(work/'server/ops.json').write_text(json.dumps([dict(uuid=id,name='Tester',level=4,bypassesPlayerLimit=True)]))
env=os.environ.copy()
java=str(Path(env['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in env else 'java'
with (work/'prepare.log').open('w') as log:
 subprocess.run([str(root/'gradlew'),'-I',str(init),'--project-cache-dir',str(work/'gradle'),'-PrivetUiHarness',':mod:classes',':mod:compileCompatibilityFixturesJava',':mod:compileUiHarnessJava',':mod:prepareServerRun',':mod:prepareClientRun','--no-daemon','--console=plain'],env=env,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=600)
env['MOD_CLASSES']=os.pathsep.join('rivet%%'+str(work/'build'/p) for p in ['mod/classes/java/main','mod/resources/main','mod/classes/java/uiHarness','core/classes/java/main','core/resources/main','api/classes/java/main'])+os.pathsep+'rivet_compat_tests%%'+str(work/'build/mod/classes/java/compatibilityFixtures')
r=work/'fixtures/META-INF';r.mkdir(parents=True,exist_ok=True);(r/'neoforge.mods.toml').write_text('modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="rivet_compat_tests"\nversion="1.0.0"\ndisplayName="Rivet tests"\n')
env['MOD_CLASSES']+=os.pathsep+'rivet_compat_tests%%'+str(r.parent)
name='rivet-adapters-'+uuid.uuid4().hex[:12];env['POSTGRES_PASSWORD']=secrets.token_hex(24)
subprocess.run(['docker','run','-d','--name',name,'-p','127.0.0.1:'+str(args.database_port)+':5432','-e','POSTGRES_PASSWORD','-e','POSTGRES_USER=rivet_test','-e','POSTGRES_DB=rivet_test','postgres:18'],env=env,check=True,stdout=subprocess.DEVNULL)
try:
 for i in range(40):
  if subprocess.run(['docker','exec',name,'pg_isready','-U','rivet_test'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0:break
  time.sleep(1)
 env['RIVET_TEST_DB_PASSWORD']=env.pop('POSTGRES_PASSWORD')
 for scenario in (args.scenario or ['core','access','waystones','access-server']):
  run=env.copy();servermarkers=[];clientmarkers=[]
  if scenario=='core':
   run.update(RIVET_NUMISMATICS_TEST='bridge',RIVET_NUMISMATICS_ADDRESS=address,RIVET_ALL_ADAPTERS_TEST='true',RIVET_OPTIONAL_ADAPTER_TEST='true');ready='RIVET_NUMISMATICS_TEST_OK';servermarkers=[ready,'RIVET_OPTIONAL_ADAPTER_TEST_OK','RIVET_DUAL_VOICE_TEST_OK'];clientmarkers=['RIVET_NUMISMATICS_CLIENT_OK','RIVET_ALL_ADAPTERS_CLIENT_OK']
  elif scenario=='access':
   run.update(RIVET_COMPAT_SMOKE='bridge',RIVET_COMPAT_ADDRESS=address);ready='RIVET_COMPAT_BRIDGE_READY';servermarkers=[ready];clientmarkers=['RIVET_COMPAT_BRIDGE_OK']
  elif scenario=='waystones':
   run.update(RIVET_WAYSTONES_TEST='true',RIVET_WAYSTONES_ADDRESS=address);ready='Done (';servermarkers=['RIVET_WAYSTONES_SERVER_OK'];clientmarkers=['RIVET_WAYSTONES_CLIENT_OK']
  else:
   run.update(RIVET_COMPAT_SMOKE='present');ready='RIVET_COMPAT_SERVER_OK';servermarkers=[ready]
  slog=work/(scenario+'-server.log');clog=work/(scenario+'-client.log')
  with slog.open('w') as out:
   server=subprocess.Popen(command(work,java,'server'),cwd=work/'server',env=run,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT)
   try:
    for i in range(100):
     text=slog.read_text(errors='replace')
     if 'FAILED' in text:raise RuntimeError('Server fixture failed: '+str(slog))
     if ready in text:break
     if server.poll() is not None:raise RuntimeError('Server stopped: '+str(slog))
     time.sleep(1)
    else:raise RuntimeError('Server readiness timeout: '+str(slog))
    if clientmarkers:
     with clog.open('w') as cout:
      client=subprocess.Popen(command(work,java,'client')+['--username','Tester','--width','1280','--height','1024'],cwd=work/'client',env=run,stdout=cout,stderr=subprocess.STDOUT)
      try:
       for attempt in range(180):
        text=clog.read_text(errors='replace')
        if 'RIVET_' in text and 'FAILED' in text:raise RuntimeError('Client fixture failed: '+str(clog))
        if all(marker in text for marker in clientmarkers):break
        if client.poll() is not None:raise RuntimeError('Client stopped: '+str(clog))
        time.sleep(1)
       else:raise RuntimeError('Client timeout: '+str(clog))
      finally:stop(client)
     for marker in clientmarkers:verify(clog,marker)
    else:server.wait(timeout=45)
    for marker in servermarkers:verify(slog,marker)
   finally:
    if server.poll() is None:
     try:
      server.stdin.write(b'stop\n');server.stdin.flush();server.wait(timeout=30)
     except subprocess.TimeoutExpired:
      with (work/(scenario+'-shutdown.txt')).open('w') as dump:
       subprocess.run([str(Path(java).with_name('jstack')),str(server.pid)],stdout=dump,stderr=subprocess.STDOUT,timeout=15)
      try:server.wait(timeout=90)
      except subprocess.TimeoutExpired:
       stop(server)
       raise RuntimeError('Server shutdown timeout: '+str(slog))
     except OSError:
      stop(server)
      raise
    if server.returncode!=0:raise RuntimeError('Server exit '+str(server.returncode)+': '+str(slog))
   print('RIVET_SEVEN_SCENARIO_OK '+scenario,flush=True)
finally:subprocess.run(['docker','rm','-f','-v',name],stdout=subprocess.DEVNULL)
