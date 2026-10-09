       IDENTIFICATION DIVISION.
       PROGRAM-ID. DEPTHWITNESS.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 UNUSED-FIELD PIC X.
       PROCEDURE DIVISION.
       MAIN.
       PERFORM P-0.
       CALL 'AFTER'.
       GOBACK.
       P-0.
       PERFORM P-1.
       EXIT.
       P-1.
       PERFORM P-2.
       EXIT.
       P-2.
       PERFORM P-3.
       EXIT.
       P-3.
       PERFORM P-4.
       EXIT.
       P-4.
       PERFORM P-5.
       EXIT.
       P-5.
       PERFORM P-6.
       EXIT.
       P-6.
       PERFORM P-7.
       EXIT.
       P-7.
       PERFORM P-8.
       EXIT.
       P-8.
       PERFORM P-9.
       EXIT.
       P-9.
       PERFORM P-10.
       EXIT.
       P-10.
       PERFORM P-11.
       EXIT.
       P-11.
       CALL 'DEEPCALL'.
       EXIT.
