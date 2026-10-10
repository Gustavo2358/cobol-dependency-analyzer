       IDENTIFICATION DIVISION.
       PROGRAM-ID. WCORRELATION.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 P PIC X(8).
       01 F PIC 9.
       PROCEDURE DIVISION.
       ACCEPT F.
       IF F = 1
       MOVE 'LEFT' TO P
       ELSE
       MOVE 'RIGHT' TO P
       END-IF.
       IF F = 1
       CALL P
       END-IF.
       GOBACK.
