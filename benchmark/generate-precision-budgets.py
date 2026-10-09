#!/usr/bin/env python3
"""Small semantic witnesses for depth truncation and context merging."""
import argparse, hashlib, json
from pathlib import Path

def program(name, data, body):
    text=f'IDENTIFICATION DIVISION.\nPROGRAM-ID. {name}.\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n{data}\nPROCEDURE DIVISION.\n{body}\n'
    return ''.join('       '+line+'\n' for line in text.splitlines()).rstrip()+'\n'

def cases():
    result=[]
    for depth in (12,32):
        body="MAIN.\nPERFORM P-0.\nCALL 'AFTER'.\nGOBACK.\n"
        for i in range(depth):
            body+=f'P-{i}.\n'+(f'PERFORM P-{i+1}.\n' if i+1<depth else "CALL 'DEEPCALL'.\n")+'EXIT.\n'
        result.append((f'depth-{depth}',program('DEPTHWITNESS','01 UNUSED-FIELD PIC X.',body),['AFTER','DEEPCALL'],False))
    result.append(('caller-correlation',program('CALLERCORRELATION',
        '01 FLAG PIC X.\n01 LOOP-FLAG PIC 9 VALUE 0.\n01 TARGET PIC X(8).',
        "MAIN.\nMOVE '0' TO FLAG.\nPERFORM BODY.\nCALL TARGET.\nMOVE '1' TO FLAG.\nPERFORM BODY.\nGOBACK.\n"
        "BODY.\nIF FLAG = '0'\nMOVE 'ONLY' TO TARGET\nELSE\nMOVE 'UNOBS' TO TARGET\nEND-IF.\n"
        "IF LOOP-FLAG = 1 GO TO BODY END-IF.\nEXIT."),['ONLY'],False))
    result.append(('full-unknown-write',program('UNKNOWNWRITE',
        '01 TARGET PIC X(8).\n01 SOURCE-VALUE PIC X(8).',
        "MAIN.\nMOVE 'OLD' TO TARGET.\nPERFORM BODY.\nCALL TARGET.\nGOBACK.\n"
        "BODY.\nMOVE SOURCE-VALUE TO TARGET.\nEXIT."),[],True))
    result.append(('section-escape',program('SECTIONESCAPE','01 TARGET PIC X(8).',
        "MAIN.\nPERFORM WORK-SECTION.\nCALL TARGET.\nGOBACK.\nWORK-SECTION SECTION.\n"
        "A.\nMOVE 'GOOD' TO TARGET.\nEXIT SECTION.\nB.\nMOVE 'BAD' TO TARGET."),['GOOD'],False))
    result.append(('terminal-no-resume',program('TERMINALWITNESS','01 UNUSED-FIELD PIC X.',
        "MAIN.\nPERFORM A THRU B.\nCALL 'UNREACH'.\nGOBACK.\n"
        "A.\nCALL 'REACHED'.\nGOBACK.\nB.\nCONTINUE."),['REACHED'],False))
    result.append(('conditional-passthrough',program('CONDITIONALPASS',
        '01 FLAG PIC X.\n01 TARGET PIC X(8).',
        "MAIN.\nMOVE 'KEEP' TO TARGET.\nPERFORM BODY.\nCALL TARGET.\nGOBACK.\n"
        "BODY.\nIF FLAG = 'Y'\nMOVE 'NEW' TO TARGET\nELSE\nCONTINUE\nEND-IF.\nEXIT.\n"
        "TAIL.\nMOVE 'POISON' TO TARGET.\nGOBACK."),['KEEP','NEW'],False))
    for count in (12,24):
        body='MAIN.\n'
        for i in range(count):
            body+=f"MOVE 'MODE{i:04d}' TO FLAG.\nPERFORM BODY.\n"
            if i+1<count:body+='CALL TARGET.\n'
        body+='GOBACK.\nBODY.\n'
        for i in range(count):body+=f"IF FLAG = 'MODE{i:04d}' MOVE 'PGM{i:05d}' TO TARGET END-IF.\n"
        body+='IF LOOP-FLAG = 1 GO TO BODY END-IF.\nEXIT.'
        result.append((f'caller-many-{count}',program('MANYCALLERS',
            "01 FLAG PIC X(8).\n01 LOOP-FLAG PIC 9 VALUE 0.\n01 TARGET PIC X(8) VALUE 'INITIAL'.",body),
            [f'PGM{i:05d}' for i in range(count-1)],False))
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=False);rows=[]
    for name,text,expected,unknown in cases():
        path=a.output/(name+'.cbl');path.write_text(text)
        rows.append(dict(id=name,source=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),expected=expected,expectedUnknown=unknown))
    (a.output/'expected.json').write_text(json.dumps(rows,indent=2)+'\n')
if __name__=='__main__':main()
