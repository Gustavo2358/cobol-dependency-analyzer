       *> Diagnostic sentinel: the ablation must lose ESCAPED.
       IDENTIFICATION DIVISION.
       PROGRAM-ID. ESCAPESENTINEL.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 MODE-FLAG PIC 9.
       PROCEDURE DIVISION.
       MAIN.
           ACCEPT MODE-FLAG.
           PERFORM A.
           CALL 'AFTER'.
           GOBACK.
       A.
           IF MODE-FLAG = 1
               EXIT PARAGRAPH
           END-IF.
           GO TO B.
       B.
           EXIT PARAGRAPH.
       C.
           CALL 'ESCAPED'.
           GO TO A.
