from pathlib import Path
import tempfile,subprocess,textwrap,re,json,os
from datetime import datetime,timezone
root=Path(__file__).resolve().parents[3]
source=root/'docs/ci-cd/backend/.github/workflows/ci-publicar-backend.yml'
match=re.search(r'(?ms)^      - name: Conservar migraciones SQL ya existentes.*?^        run: \|\n(.*?)(?=^      - |\Z)',source.read_text())
script=textwrap.dedent(match.group(1))
assert 'git diff --name-status' in script
checks=[]
for name,expected in [('sin_cambios',0),('agregar_nueva',0),('modificar_existente',1),('eliminar_existente',1),('renombrar_existente',1)]:
    with tempfile.TemporaryDirectory(prefix='semillero-guardia-') as d:
        path=Path(d)
        def git(*args):
            return subprocess.check_output(['git',*args],cwd=path,text=True,stderr=subprocess.STDOUT).strip()
        git('init','-q','--initial-branch=main')
        migrations=path/'database/migrations';migrations.mkdir(parents=True)
        existing=migrations/'V000__base.sql';existing.write_text('create table ejemplo(id bigint primary key);\n')
        git('add','database')
        git('-c','user.name=Semillero QA','-c','user.email=qa@example.invalid','commit','-qm','Base de prueba')
        base=git('rev-parse','HEAD')
        if name=='agregar_nueva':(migrations/'V001__nuevo.sql').write_text('alter table ejemplo add column nombre text;\n')
        elif name=='modificar_existente':existing.write_text('create table ejemplo(id text primary key);\n')
        elif name=='eliminar_existente':existing.unlink()
        elif name=='renombrar_existente':existing.rename(migrations/'V000__renombrada.sql')
        if name!='sin_cambios':
            git('add','-A')
            git('-c','user.name=Semillero QA','-c','user.email=qa@example.invalid','commit','-qm','Variante de prueba')
        head=git('rev-parse','HEAD')
        result=subprocess.run(['bash','-c',script],cwd=path,env={**os.environ,'BASE_SHA':base,'GITHUB_SHA':head},capture_output=True,text=True)
        checks.append({'caso':name,'esperadoExit':expected,'obtenidoExit':result.returncode,'passed':result.returncode==expected,'salida':result.stdout+result.stderr})
report={'fechaUtc':datetime.now(timezone.utc).isoformat(),'alcance':'Bloque Bash exacto de la propuesta de workflow, ejecutado en repositorios temporales locales; no es una ejecución de GitHub Actions.','checks':checks}
(root/'.local/vps/casos-excel-20261102').mkdir(parents=True,exist_ok=True)
(root/'.local/vps/casos-excel-20261102/guardia-migraciones.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
assert all(c['passed'] for c in checks)
