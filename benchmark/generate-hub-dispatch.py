#!/usr/bin/env python3
"""External dispatch loops: each box returns to the next hub; FINAL terminates."""
import argparse, hashlib, json
from pathlib import Path

MODES = ('literal', 'value', 'flags', 'perform', 'perform-flags', 'cross-perform',
         'hub-perform', 'hub-perform-flags')

def source(boxes, hubs, mode):
    if not 1 <= boxes <= 254 or hubs < 1:
        raise ValueError('use 1..254 boxes plus FINAL, and at least one hub')
    if mode not in MODES:
        raise ValueError('unknown mode')
    lines = ['*> External selector is refreshed on every dispatch.',
             'IDENTIFICATION DIVISION.', 'PROGRAM-ID. HUBDISPATCH.',
             'DATA DIVISION.', 'WORKING-STORAGE SECTION.',
             "01 TARGET-PGM PIC X(8) VALUE 'BOOT0000'.",
             '01 EXTERNAL-FLAG PIC 9.']
    for h in range(hubs):
        lines.append(f'01 INPUT-{h:02d} PIC 9(3).')
    if mode in ('flags', 'perform-flags', 'hub-perform-flags'):
        for b in range(boxes):
            lines.append(f'01 FLAG-{b:03d} PIC X VALUE SPACE.')
    lines.extend(['PROCEDURE DIVISION.', 'MAIN.', '    GO TO HUB-00.'])
    for h in range(hubs):
        lines.append(f'HUB-{h:02d}.')
        if mode in ('value', 'hub-perform'):
            lines.append('    CALL TARGET-PGM.')
        lines.extend([f'    ACCEPT INPUT-{h:02d}.', '    GO TO'])
        targets = [f'B{h:02d}-{b:03d}' for b in range(boxes)] + ['FINAL-BOX']
        for start in range(0, len(targets), 4):
            lines.append('        ' + ' '.join(targets[start:start+4]))
        lines.extend([f'        DEPENDING ON INPUT-{h:02d}.', f'    GO TO HUB-{h:02d}.'])
    for h in range(hubs):
        for b in range(boxes):
            lines.append(f'B{h:02d}-{b:03d}.')
            if mode in ('literal', 'cross-perform'):
                lines.append(f"    CALL 'PGM{b:05d}'.")
                if mode == 'cross-perform':
                    lines.extend(['    IF EXTERNAL-FLAG = 1',
                                  f'        PERFORM B{(h+1)%hubs:02d}-{(b+1)%boxes:03d}',
                                  '    END-IF.'])
            elif mode in ('value', 'perform', 'hub-perform'):
                lines.append(f"    MOVE 'PGM{b:05d}' TO TARGET-PGM.")
                if mode == 'perform':
                    lines.append('    PERFORM COMMON-BOX.')
                if mode == 'hub-perform':
                    lines.append(f'    PERFORM HUB-{(h+1)%hubs:02d}.')
            else:
                lines.append(f"    MOVE 'Y' TO FLAG-{b:03d}.")
                if mode == 'perform-flags':
                    lines.append('    PERFORM COMMON-BOX.')
                if mode == 'hub-perform-flags':
                    lines.append(f'    PERFORM HUB-{(h+1)%hubs:02d}.')
            lines.append(f'    GO TO HUB-{(h+1)%hubs:02d}.')
    lines.append('FINAL-BOX.')
    if mode == 'perform-flags':
        lines.extend(['    GOBACK.', 'COMMON-BOX.'])
    if mode in ('flags', 'perform-flags', 'hub-perform-flags'):
        for b in range(boxes):
            lines.extend([f"    IF FLAG-{b:03d} = 'Y'", f"        CALL 'PGM{b:05d}'",
                          '    ELSE', "        CALL 'ZERO0000'", '    END-IF.'])
    lines.append('    EXIT.' if mode == 'perform-flags' else '    GOBACK.')
    if mode == 'perform':
        lines.extend(['COMMON-BOX.', '    CALL TARGET-PGM.', '    EXIT.'])
    return ''.join('       ' + line + '\n' for line in lines)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--boxes', type=int, default=254)
    p.add_argument('--hubs', type=int, default=2)
    p.add_argument('--mode', choices=MODES, default='literal')
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    text = source(a.boxes, a.hubs, a.mode)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(text)
    expected = [f'PGM{b:05d}' for b in range(a.boxes)]
    if a.mode in ('value', 'hub-perform'): expected.append('BOOT0000')
    if a.mode in ('flags', 'perform-flags', 'hub-perform-flags'): expected.append('ZERO0000')
    print(json.dumps(dict(boxes=a.boxes, hubs=a.hubs, mode=a.mode,
                         sha256=hashlib.sha256(text.encode()).hexdigest(), expected=sorted(expected))))


if __name__ == '__main__':
    main()
