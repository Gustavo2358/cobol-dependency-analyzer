       IDENTIFICATION DIVISION.
       PROGRAM-ID. ACTAND01.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-FLAG PIC X.
       PROCEDURE DIVISION.
       MAIN.
       PERFORM P-001 THRU P-076.
       PERFORM P-002 THRU P-076.
       PERFORM P-003 THRU P-076.
       PERFORM P-004 THRU P-076.
       PERFORM P-005 THRU P-076.
       PERFORM P-006 THRU P-076.
       PERFORM P-007 THRU P-076.
       PERFORM P-008 THRU P-076.
       PERFORM P-009 THRU P-076.
       PERFORM P-010 THRU P-076.
       PERFORM P-011 THRU P-076.
       PERFORM P-012 THRU P-076.
       PERFORM P-013 THRU P-076.
       PERFORM P-014 THRU P-076.
       PERFORM P-015 THRU P-076.
       PERFORM P-016 THRU P-076.
       PERFORM P-017 THRU P-076.
       PERFORM P-018 THRU P-076.
       PERFORM P-019 THRU P-076.
       PERFORM P-020 THRU P-076.
       PERFORM P-021 THRU P-076.
       PERFORM P-022 THRU P-076.
       PERFORM P-023 THRU P-076.
       PERFORM P-024 THRU P-076.
       PERFORM P-025 THRU P-076.
       PERFORM P-026 THRU P-076.
       PERFORM P-027 THRU P-076.
       PERFORM P-028 THRU P-076.
       PERFORM P-029 THRU P-076.
       PERFORM P-030 THRU P-076.
       PERFORM P-031 THRU P-076.
       PERFORM P-032 THRU P-076.
       PERFORM P-033 THRU P-076.
       PERFORM P-034 THRU P-076.
       PERFORM P-035 THRU P-076.
       PERFORM P-036 THRU P-076.
       PERFORM P-037 THRU P-076.
       PERFORM P-038 THRU P-076.
       PERFORM P-039 THRU P-076.
       PERFORM P-040 THRU P-076.
       PERFORM P-041 THRU P-076.
       PERFORM P-042 THRU P-076.
       PERFORM P-043 THRU P-076.
       PERFORM P-044 THRU P-076.
       PERFORM P-045 THRU P-076.
       PERFORM P-046 THRU P-076.
       PERFORM P-047 THRU P-076.
       PERFORM P-048 THRU P-076.
       PERFORM P-049 THRU P-076.
       PERFORM P-050 THRU P-076.
       PERFORM P-051 THRU P-076.
       PERFORM P-052 THRU P-076.
       PERFORM P-053 THRU P-076.
       PERFORM P-054 THRU P-076.
       PERFORM P-055 THRU P-076.
       PERFORM P-056 THRU P-076.
       PERFORM P-057 THRU P-076.
       PERFORM P-058 THRU P-076.
       PERFORM P-059 THRU P-076.
       PERFORM P-060 THRU P-076.
       PERFORM P-061 THRU P-076.
       PERFORM P-062 THRU P-076.
       PERFORM P-063 THRU P-076.
       PERFORM P-064 THRU P-076.
       PERFORM P-065 THRU P-076.
       PERFORM P-066 THRU P-076.
       PERFORM P-067 THRU P-076.
       PERFORM P-068 THRU P-076.
       PERFORM P-069 THRU P-076.
       PERFORM P-070 THRU P-076.
       PERFORM P-071 THRU P-076.
       PERFORM P-072 THRU P-076.
       PERFORM P-073 THRU P-076.
       PERFORM P-074 THRU P-076.
       PERFORM P-075 THRU P-076.
       PERFORM P-076 THRU P-076.
       CALL 'SYNROOT'.
       GOBACK.
       P-001.
       CONTINUE.
       P-002.
       CONTINUE.
       P-003.
       CONTINUE.
       P-004.
       CONTINUE.
       P-005.
       CONTINUE.
       P-006.
       CONTINUE.
       P-007.
       CONTINUE.
       P-008.
       CONTINUE.
       P-009.
       CONTINUE.
       P-010.
       CONTINUE.
       P-011.
       CONTINUE.
       P-012.
       CONTINUE.
       P-013.
       CONTINUE.
       P-014.
       CONTINUE.
       P-015.
       CONTINUE.
       P-016.
       CONTINUE.
       P-017.
       CONTINUE.
       P-018.
       CONTINUE.
       P-019.
       CONTINUE.
       P-020.
       CONTINUE.
       P-021.
       CONTINUE.
       P-022.
       CONTINUE.
       P-023.
       CONTINUE.
       P-024.
       CONTINUE.
       P-025.
       CONTINUE.
       P-026.
       CONTINUE.
       P-027.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-028.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-029.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-030.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-031.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-032.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-033.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-034.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-035.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-036.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-037.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-038.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-039.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-040.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-041.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-042.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-043.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-044.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-045.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-046.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-047.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-048.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-049.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-050.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-051.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-052.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-053.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-054.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-055.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-056.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-057.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-058.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-059.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-060.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-061.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-062.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-063.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-064.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-065.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-066.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-067.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-068.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-069.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-070.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-071.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-072.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-073.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-074.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-075.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       P-076.
       IF WS-FLAG = 'Y'
       PERFORM SYNHELP-A
       ELSE
       PERFORM SYNHELP-B
       END-IF.
       EXIT.
       SYNHELP-A.
       CALL 'SYNTGTA'.
       EXIT.
       SYNHELP-B.
       CALL 'SYNTGTB'.
       EXIT.
