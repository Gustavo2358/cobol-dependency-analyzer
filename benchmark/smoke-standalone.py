#!/usr/bin/env python3
"""Execute the packaged CLI from a temporary directory with an empty environment."""
from pathlib import Path
import tempfile,shutil,subprocess,json,sys
jar=Path(sys.argv[1]).resolve();java=shutil.which('java')
with tempfile.TemporaryDirectory(prefix='cobol-independent-') as name:
 root=Path(name);shutil.copy2(jar,root/'analyzer.jar')
 source=['IDENTIFICATION DIVISION.','PROGRAM-ID. ISOLATED.','DATA DIVISION.','WORKING-STORAGE SECTION.',"01 TARGET PIC X(8) VALUE 'PROGA'.",'PROCEDURE DIVISION.',"MOVE 'PROGB' TO TARGET.",'CALL TARGET.','GOBACK.']
 (root/'test.cbl').write_text(''.join('       '+s+'\n' for s in source))
 p=subprocess.run([java,'-Xmx256m','-jar','analyzer.jar','--source','test.cbl','--output','dependencies.json'],cwd=root,env={'LANG':'C.UTF-8'},capture_output=True,text=True,timeout=30)
 assert p.returncode==0,p.stderr
 assert json.loads((root/'dependencies.json').read_text())=={'program':'ISOLATED','dependencies':[{'type':'program','name':'PROGB','at':{'file':'test.cbl','line':8}}]}
 assert sorted(p.name for p in root.iterdir())==['analyzer.jar','dependencies.json','test.cbl']
 print('PASS: standalone shaded JAR, isolated cwd/environment, minimal dynamic result')
