       *> External selector is refreshed on every dispatch.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. HUBDISPATCH.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 TARGET-PGM PIC X(8) VALUE 'BOOT0000'.
       01 EXTERNAL-FLAG PIC 9.
       01 INPUT-00 PIC 9(3).
       01 INPUT-01 PIC 9(3).
       01 FLAG-000 PIC X VALUE SPACE.
       01 FLAG-001 PIC X VALUE SPACE.
       01 FLAG-002 PIC X VALUE SPACE.
       01 FLAG-003 PIC X VALUE SPACE.
       01 FLAG-004 PIC X VALUE SPACE.
       01 FLAG-005 PIC X VALUE SPACE.
       01 FLAG-006 PIC X VALUE SPACE.
       01 FLAG-007 PIC X VALUE SPACE.
       PROCEDURE DIVISION.
       MAIN.
           GO TO HUB-00.
       HUB-00.
           ACCEPT INPUT-00.
           GO TO
               B00-000 B00-001 B00-002 B00-003
               B00-004 B00-005 B00-006 B00-007
               FINAL-BOX
               DEPENDING ON INPUT-00.
           GO TO HUB-00.
       HUB-01.
           ACCEPT INPUT-01.
           GO TO
               B01-000 B01-001 B01-002 B01-003
               B01-004 B01-005 B01-006 B01-007
               FINAL-BOX
               DEPENDING ON INPUT-01.
           GO TO HUB-01.
       B00-000.
           MOVE 'Y' TO FLAG-000.
           PERFORM HUB-01.
           GO TO HUB-01.
       B00-001.
           MOVE 'Y' TO FLAG-001.
           PERFORM HUB-01.
           GO TO HUB-01.
       B00-002.
           MOVE 'Y' TO FLAG-002.
           PERFORM HUB-01.
           GO TO HUB-01.
       B00-003.
           MOVE 'Y' TO FLAG-003.
           PERFORM HUB-01.
           GO TO HUB-01.
       B00-004.
           MOVE 'Y' TO FLAG-004.
           PERFORM HUB-01.
           GO TO HUB-01.
       B00-005.
           MOVE 'Y' TO FLAG-005.
           PERFORM HUB-01.
           GO TO HUB-01.
       B00-006.
           MOVE 'Y' TO FLAG-006.
           PERFORM HUB-01.
           GO TO HUB-01.
       B00-007.
           MOVE 'Y' TO FLAG-007.
           PERFORM HUB-01.
           GO TO HUB-01.
       B01-000.
           MOVE 'Y' TO FLAG-000.
           PERFORM HUB-00.
           GO TO HUB-00.
       B01-001.
           MOVE 'Y' TO FLAG-001.
           PERFORM HUB-00.
           GO TO HUB-00.
       B01-002.
           MOVE 'Y' TO FLAG-002.
           PERFORM HUB-00.
           GO TO HUB-00.
       B01-003.
           MOVE 'Y' TO FLAG-003.
           PERFORM HUB-00.
           GO TO HUB-00.
       B01-004.
           MOVE 'Y' TO FLAG-004.
           PERFORM HUB-00.
           GO TO HUB-00.
       B01-005.
           MOVE 'Y' TO FLAG-005.
           PERFORM HUB-00.
           GO TO HUB-00.
       B01-006.
           MOVE 'Y' TO FLAG-006.
           PERFORM HUB-00.
           GO TO HUB-00.
       B01-007.
           MOVE 'Y' TO FLAG-007.
           PERFORM HUB-00.
           GO TO HUB-00.
       FINAL-BOX.
           IF FLAG-000 = 'Y'
               CALL 'PGM00000'
           ELSE
               CALL 'ZERO0000'
           END-IF.
           IF FLAG-001 = 'Y'
               CALL 'PGM00001'
           ELSE
               CALL 'ZERO0000'
           END-IF.
           IF FLAG-002 = 'Y'
               CALL 'PGM00002'
           ELSE
               CALL 'ZERO0000'
           END-IF.
           IF FLAG-003 = 'Y'
               CALL 'PGM00003'
           ELSE
               CALL 'ZERO0000'
           END-IF.
           IF FLAG-004 = 'Y'
               CALL 'PGM00004'
           ELSE
               CALL 'ZERO0000'
           END-IF.
           IF FLAG-005 = 'Y'
               CALL 'PGM00005'
           ELSE
               CALL 'ZERO0000'
           END-IF.
           IF FLAG-006 = 'Y'
               CALL 'PGM00006'
           ELSE
               CALL 'ZERO0000'
           END-IF.
           IF FLAG-007 = 'Y'
               CALL 'PGM00007'
           ELSE
               CALL 'ZERO0000'
           END-IF.
           GOBACK.
