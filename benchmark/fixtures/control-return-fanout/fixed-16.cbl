       *> External selector is refreshed on every dispatch.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. HUBDISPATCH.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 TARGET-PGM PIC X(8) VALUE 'BOOT0000'.
       01 EXTERNAL-FLAG PIC 9 VALUE 0.
       01 INPUT-00 PIC 9(3).
       01 INPUT-01 PIC 9(3).
       PROCEDURE DIVISION.
       MAIN.
           MOVE 0 TO EXTERNAL-FLAG.
           GO TO HUB-00.
       HUB-00.
           ACCEPT INPUT-00.
           GO TO
               B00-000 B00-001 B00-002 B00-003
               B00-004 B00-005 B00-006 B00-007
               B00-008 B00-009 B00-010 B00-011
               B00-012 B00-013 B00-014 B00-015
               FINAL-BOX
               DEPENDING ON INPUT-00.
           GO TO HUB-00.
       HUB-01.
           ACCEPT INPUT-01.
           GO TO
               B01-000 B01-001 B01-002 B01-003
               B01-004 B01-005 B01-006 B01-007
               B01-008 B01-009 B01-010 B01-011
               B01-012 B01-013 B01-014 B01-015
               FINAL-BOX
               DEPENDING ON INPUT-01.
           GO TO HUB-01.
       B00-000.
           CALL 'PGM00000'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-001
           END-IF.
           GO TO HUB-01.
       B00-001.
           CALL 'PGM00001'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-002
           END-IF.
           GO TO HUB-01.
       B00-002.
           CALL 'PGM00002'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-003
           END-IF.
           GO TO HUB-01.
       B00-003.
           CALL 'PGM00003'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-004
           END-IF.
           GO TO HUB-01.
       B00-004.
           CALL 'PGM00004'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-005
           END-IF.
           GO TO HUB-01.
       B00-005.
           CALL 'PGM00005'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-006
           END-IF.
           GO TO HUB-01.
       B00-006.
           CALL 'PGM00006'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-007
           END-IF.
           GO TO HUB-01.
       B00-007.
           CALL 'PGM00007'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-008
           END-IF.
           GO TO HUB-01.
       B00-008.
           CALL 'PGM00008'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-009
           END-IF.
           GO TO HUB-01.
       B00-009.
           CALL 'PGM00009'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-010
           END-IF.
           GO TO HUB-01.
       B00-010.
           CALL 'PGM00010'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-011
           END-IF.
           GO TO HUB-01.
       B00-011.
           CALL 'PGM00011'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-012
           END-IF.
           GO TO HUB-01.
       B00-012.
           CALL 'PGM00012'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-013
           END-IF.
           GO TO HUB-01.
       B00-013.
           CALL 'PGM00013'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-014
           END-IF.
           GO TO HUB-01.
       B00-014.
           CALL 'PGM00014'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-015
           END-IF.
           GO TO HUB-01.
       B00-015.
           CALL 'PGM00015'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B01-000
           END-IF.
           GO TO HUB-01.
       B01-000.
           CALL 'PGM00000'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-001
           END-IF.
           GO TO HUB-00.
       B01-001.
           CALL 'PGM00001'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-002
           END-IF.
           GO TO HUB-00.
       B01-002.
           CALL 'PGM00002'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-003
           END-IF.
           GO TO HUB-00.
       B01-003.
           CALL 'PGM00003'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-004
           END-IF.
           GO TO HUB-00.
       B01-004.
           CALL 'PGM00004'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-005
           END-IF.
           GO TO HUB-00.
       B01-005.
           CALL 'PGM00005'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-006
           END-IF.
           GO TO HUB-00.
       B01-006.
           CALL 'PGM00006'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-007
           END-IF.
           GO TO HUB-00.
       B01-007.
           CALL 'PGM00007'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-008
           END-IF.
           GO TO HUB-00.
       B01-008.
           CALL 'PGM00008'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-009
           END-IF.
           GO TO HUB-00.
       B01-009.
           CALL 'PGM00009'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-010
           END-IF.
           GO TO HUB-00.
       B01-010.
           CALL 'PGM00010'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-011
           END-IF.
           GO TO HUB-00.
       B01-011.
           CALL 'PGM00011'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-012
           END-IF.
           GO TO HUB-00.
       B01-012.
           CALL 'PGM00012'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-013
           END-IF.
           GO TO HUB-00.
       B01-013.
           CALL 'PGM00013'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-014
           END-IF.
           GO TO HUB-00.
       B01-014.
           CALL 'PGM00014'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-015
           END-IF.
           GO TO HUB-00.
       B01-015.
           CALL 'PGM00015'.
           IF EXTERNAL-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           IF EXTERNAL-FLAG = 2
               PERFORM B00-000
           END-IF.
           GO TO HUB-00.
       FINAL-BOX.
           GOBACK.
