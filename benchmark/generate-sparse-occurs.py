#!/usr/bin/env python3
"""Deterministic adversaries for demanded table cells; writes only repo fixtures."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/test/resources/dependency-regression/sparse-occurs'
OUT.mkdir(parents=True,exist_ok=True)
cases=[]
def case(name,data,body,expected,unknown=False,scale=False):
 text=''.join('       '+s+'\n' for s in ('IDENTIFICATION DIVISION.\nPROGRAM-ID. SPARSE-TABLE.\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n'+data+'\nPROCEDURE DIVISION.\n'+body+'\n').splitlines())
 p=OUT/(name+'.cbl');p.write_text(text)
 cases.append(dict(id=name,source=p.name,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),expected=sorted(expected),unknown=unknown,scale=scale))
def table(n):return f'01 TABLE-DATA.\n05 DEST PIC X(8) OCCURS {n} TIMES.'
for n in [100,1000,5000,50000]:
 case(f'sparse-{n}',table(n),f"MOVE 'OLD' TO DEST(1).\nMOVE 'OTHER' TO DEST({n}).\nMOVE 'GOOD' TO DEST(1).\nCALL DEST(1).\nGOBACK.",['GOOD'],scale=True)
for n in [10,50,200]:
 case(f'nested-{n}',f'01 TABLE-DATA.\n05 ROW-X OCCURS {n} TIMES.\n10 DEST PIC X(8) OCCURS {n} TIMES.',f"MOVE 'OTHER' TO DEST({n}, {n}).\nMOVE 'GOOD' TO DEST(1, 2).\nCALL DEST(1, 2).\nGOBACK.",['GOOD'],scale=True)
case('literal-loop',table(50000)+'\n01 IDX PIC 9(6).',"PERFORM VARYING IDX FROM 1 BY 1 UNTIL IDX > 50000\nCALL 'GOOD' USING DEST(IDX)\nEND-PERFORM.\nGOBACK.",['GOOD'],scale=True)
case('computed-perform',table(1000)+'\n01 IDX PIC 9(4).',"MAIN.\nCOMPUTE IDX = 997 + 3.\nMOVE 'OTHER' TO DEST(1).\nPERFORM STORE-X.\nPERFORM READ-X.\nGOBACK.\nSTORE-X.\nMOVE 'GOOD' TO DEST(IDX).\nREAD-X.\nCALL DEST(IDX).",['GOOD','OTHER'],True)
case('unknown-content',table(1000)+'\n01 INPUT-X PIC X(8).',"MOVE 'OLD' TO DEST(3).\nMOVE INPUT-X TO DEST(3).\nCALL DEST(3).\nGOBACK.",[],True)
case('unknown-read',table(1000)+'\n01 IDX PIC 9(4).',"MOVE 'FIRST' TO DEST(1).\nMOVE 'SECOND' TO DEST(2).\nCALL DEST(IDX).\nGOBACK.",['FIRST','SECOND'],True)
case('unknown-write',table(1000)+'\n01 IDX PIC 9(4).',"MOVE 'FIRST' TO DEST(1).\nMOVE 'OTHER' TO DEST(2).\nMOVE 'NEW' TO DEST(IDX).\nCALL DEST(1).\nGOBACK.",['FIRST','NEW'],True)
case('unknown-then-known',table(1000)+'\n01 IDX PIC 9(4).',"MOVE 'OLD' TO DEST(IDX).\nMOVE 'GOOD' TO DEST(1).\nCALL DEST(1).\nGOBACK.",['GOOD'])
case('all-overwritten',table(2).replace(' TIMES.',' TIMES VALUE \'OLD\'.')+'\n01 IDX PIC 9.',"MOVE 'FIRST' TO DEST(1).\nMOVE 'SECOND' TO DEST(2).\nCALL DEST(IDX).\nGOBACK.",['FIRST','SECOND'],True)
case('shared-value',table(1000).replace(' TIMES.',' TIMES VALUE \'BASE\'.'),"CALL DEST(1000).\nGOBACK.",['BASE'],True)
case('initialize',table(1000),"MOVE 'OLD' TO DEST(1).\nINITIALIZE TABLE-DATA.\nCALL DEST(1).\nGOBACK.",[])
case('alias-write',table(1000)+'\n01 ALIAS-DATA REDEFINES TABLE-DATA.\n05 OTHER-DEST PIC X(8) OCCURS 1000 TIMES.',"MOVE 'OLD' TO DEST(1).\nMOVE 'OTHER' TO DEST(2).\nMOVE 'GOOD' TO OTHER-DEST(1).\nCALL DEST(1).\nGOBACK.",['GOOD'])
case('alias-weak',table(1000)+'\n01 ALIAS-DATA REDEFINES TABLE-DATA.\n05 OTHER-DEST PIC X(8) OCCURS 1000 TIMES.\n01 IDX PIC 9(4).',"MOVE 'FIRST' TO DEST(1).\nMOVE 'OTHER' TO DEST(2).\nMOVE 'NEW' TO OTHER-DEST(IDX).\nCALL DEST(1).\nGOBACK.",['FIRST','NEW'],True)
case('full-text',table(2),"MOVE 'FIRST   SECOND  ' TO TABLE-DATA.\nCALL DEST(2).\nGOBACK.",['SECOND'])
case('partial-cell',table(1000),"MOVE 'PROGA001' TO DEST(1).\nMOVE 'OTHER' TO DEST(2).\nMOVE 'B' TO DEST(1)(5:1).\nCALL DEST(1).\nGOBACK.",['PROGB001'])
case('distinct-inputs',table(1000)+'\n01 IDX PIC 9(4).',"MAIN.\nMOVE 1 TO IDX.\nMOVE 'FIRST' TO DEST(1).\nMOVE 'SECOND' TO DEST(2).\nPERFORM READ-X.\nMOVE 2 TO IDX.\nPERFORM READ-X.\nGOBACK.\nREAD-X.\nCALL DEST(IDX).",['FIRST','SECOND'])
case('index-through-perform',table(1000)+'\n01 IDX PIC 9(4).',"MAIN.\nMOVE 1000 TO IDX.\nMOVE 'OTHER' TO DEST(1).\nPERFORM STORE-X.\nPERFORM READ-X.\nGOBACK.\nSTORE-X.\nMOVE 'GOOD' TO DEST(IDX).\nREAD-X.\nCALL DEST(IDX).",['GOOD'])
case('arithmetic-index',table(1000)+'\n01 IDX PIC 9(4).',"MOVE 2 TO IDX.\nMOVE 'OTHER' TO DEST(1).\nMOVE 'GOOD' TO DEST(500 * IDX).\nCALL DEST(500 * IDX).\nGOBACK.",['GOOD'])
(OUT/'expected.json').write_text(json.dumps(cases,indent=2)+'\n')
print(len(cases),'fixtures')
