# Parser DATA próprio — 73 programas sem fallback

**Critério atingido: 73/73 programas do CardDemo admitidos pelo parser DATA próprio, zero fallback.** Foram reconhecidas 35.590 declarações. Todos os processos terminaram com sucesso; todos os produtos têm conteúdo equivalente ao controle ANTLR.

## Implementação e fronteira

Foram implementados FILE SECTION com FD/SD, RECORD fixo/variável/faixa e RECORDING MODE; OCCURS com limites, DEPENDING qualificado, chaves e índices; nomes da produção cobolWord; VALUE implícito, cláusulas posteriores e figurative ALL; e declarações SQL preservadas opacas. A admissão é gramatical, sem seleção por nome de programa.

O modo `--parser direct-data-lab` gera a AST diretamente para o corpo DATA, sem criar contexts ANTLR nessa região. **Lexer, preprocessing, IDENTIFICATION, ENVIRONMENT e PROCEDURE continuam no ANTLR.** Zero fallback neste relatório significa que nenhuma DATA DIVISION dos 73 programas voltou ao parser ANTLR; não significa remoção completa do ANTLR do frontend.

Construções fora deste corpus ainda podem usar fallback, preservando a compatibilidade. Não se declara suporte completo a todo COBOL. O modo padrão continua ANTLR; o PR permanece um laboratório opt-in.

## Execução completa e medidas

Uma rodada por modo, conforme solicitado: **146 execuções da CLI**, em JVMs novas, sequenciais e com a ordem dos modos alternada por programa. Nenhuma compilação concorrente. Os 44 fontes de app/ e 29 variantes UniKix são os mesmos 73 fontes do inventário anterior, com os mesmos copybooks por variante.

Código medido: `30399098a9bad8b4a92f59d905d0366f6e7d8b3b`. Temurin 25.0.4, AMD Ryzen 5 5600GT, G1, heap 256 MiB–2 GiB, JSON sem compressão, DEBUG nos dois modos. O runtime foi congelado antes da medição. Fontes, COPY, dependências e classes mantiveram seus hashes.

Parsing combinado = reconhecimento + indexação de origens + construção da AST. O tempo de CLI também inclui preprocessing, lexer, análise semântica, exportação e inicialização da JVM. CPU = user + system; RSS é pico por processo. Uma observação por modo não estima dispersão.

| Medida, soma dos 73 programas | ANTLR | Parser próprio | Redução |
| --- | ---: | ---: | ---: |
| Reconhecimento (s) | 48,978 | 42,779 | 12,7% |
| Parsing + índice + AST (s) | 58,166 | 51,248 | 11,9% |
| CLI completa (s) | 169,600 | 162,610 | 4,1% |
| CPU total (s) | 445,950 | 439,830 | 1,4% |

**3 de 73 programas** atingiram pelo menos 50% de redução no parsing combinado. Cobertura sem fallback e ganho de 50% são critérios distintos; esta ampliação fecha a cobertura. Ganhos pequenos e regressões de tempo nesta única rodada estão preservados na tabela, sem interpretação de significância estatística.

## Resultados por programa

Todos os programas abaixo: **próprio, uma DATA DIVISION, zero fallback**. Setas ANTLR → próprio.

| Programa | Declarações | Parsing combinado (ms) | Redução | CLI (s) | CPU (s) | Pico RSS (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| authorization-ims-db2-mq/CBPAUP0C.cbl | 115 | 460,3 → 357,0 | 22,4% | 2,05 → 1,88 | 6,06 → 5,90 | 280,0 → 295,7 |
| authorization-ims-db2-mq/COPAUA0C.cbl | 2787 | 1592,8 → 736,8 | 53,7% | 3,85 → 3,16 | 10,17 → 9,86 | 482,3 → 384,0 |
| authorization-ims-db2-mq/COPAUS0C.cbl | 1116 | 1407,2 → 1243,2 | 11,7% | 3,30 → 3,15 | 8,23 → 8,17 | 351,1 → 357,4 |
| authorization-ims-db2-mq/COPAUS1C.cbl | 644 | 868,8 → 779,9 | 10,2% | 2,47 → 2,35 | 6,83 → 6,44 | 353,6 → 333,8 |
| authorization-ims-db2-mq/COPAUS2C.cbl | 127 | 246,7 → 238,4 | 3,4% | 1,47 → 1,43 | 4,62 → 4,28 | 284,9 → 243,6 |
| authorization-ims-db2-mq/DBUNLDGS.CBL | 162 | 302,1 → 257,1 | 14,9% | 1,52 → 1,51 | 4,34 → 4,84 | 277,7 → 284,3 |
| authorization-ims-db2-mq/PAUDBLOD.CBL | 152 | 271,1 → 256,4 | 5,4% | 1,58 → 1,52 | 4,80 → 4,80 | 289,3 → 265,1 |
| authorization-ims-db2-mq/PAUDBUNL.CBL | 142 | 281,9 → 256,5 | 9,0% | 1,56 → 1,49 | 4,67 → 4,76 | 288,2 → 271,2 |
| transaction-type-db2/COBTUPDT.cbl | 46 | 234,9 → 224,2 | 4,5% | 1,43 → 1,41 | 4,49 → 4,55 | 269,5 → 273,8 |
| transaction-type-db2/COTRTLIC.cbl | 1008 | 1800,5 → 1631,1 | 9,4% | 3,88 → 3,74 | 9,58 → 9,80 | 347,4 → 385,6 |
| transaction-type-db2/COTRTUPC.cbl | 649 | 981,6 → 884,1 | 9,9% | 2,79 → 2,67 | 7,56 → 7,39 | 346,5 → 342,7 |
| vsam-mq/COACCT01.cbl | 2582 | 1479,2 → 608,7 | 58,9% | 3,63 → 2,71 | 9,71 → 8,24 | 457,4 → 335,8 |
| vsam-mq/CODATE01.cbl | 2539 | 1413,2 → 530,0 | 62,5% | 3,50 → 2,58 | 9,33 → 7,96 | 457,3 → 343,4 |
| core/CBACT01C.cbl | 116 | 333,0 → 333,6 | -0,2% | 1,63 → 1,66 | 4,87 → 5,22 | 282,9 → 303,0 |
| core/CBACT02C.cbl | 30 | 248,0 → 253,5 | -2,2% | 1,37 → 1,40 | 4,18 → 4,41 | 262,8 → 263,3 |
| core/CBACT03C.cbl | 27 | 244,5 → 255,8 | -4,6% | 1,38 → 1,40 | 4,23 → 4,35 | 261,1 → 262,3 |
| core/CBACT04C.cbl | 138 | 471,4 → 445,1 | 5,6% | 1,87 → 1,88 | 5,62 → 5,86 | 314,7 → 308,1 |
| core/CBCUS01C.cbl | 42 | 256,2 → 245,4 | 4,2% | 1,42 → 1,39 | 4,37 → 4,21 | 264,3 → 261,7 |
| core/CBEXPORT.cbl | 175 | 311,2 → 255,2 | 18,0% | 1,67 → 1,61 | 5,39 → 5,20 | 306,4 → 293,7 |
| core/CBIMPORT.cbl | 173 | 323,7 → 256,0 | 20,9% | 1,66 → 1,61 | 5,12 → 4,98 | 285,3 → 285,9 |
| core/CBSTM03A.CBL | 224 | 418,0 → 394,6 | 5,6% | 2,05 → 1,98 | 6,48 → 6,39 | 326,6 → 317,3 |
| core/CBSTM03B.CBL | 39 | 207,5 → 193,2 | 6,9% | 1,33 → 1,33 | 3,83 → 4,08 | 241,9 → 262,2 |
| core/CBTRN01C.cbl | 132 | 401,2 → 362,8 | 9,6% | 1,75 → 1,70 | 5,33 → 5,25 | 292,7 → 291,2 |
| core/CBTRN02C.cbl | 148 | 460,1 → 424,4 | 7,8% | 1,91 → 1,89 | 6,04 → 6,01 | 312,0 → 322,1 |
| core/CBTRN03C.cbl | 139 | 585,0 → 555,4 | 5,1% | 2,08 → 2,01 | 6,16 → 5,94 | 314,9 → 296,8 |
| core/COACTUPC.cbl | 1562 | 2784,5 → 2499,5 | 10,2% | 5,50 → 5,28 | 12,67 → 12,49 | 457,5 → 392,2 |
| core/COACTVWC.cbl | 815 | 1351,6 → 1260,0 | 6,8% | 3,11 → 3,04 | 7,96 → 8,05 | 362,6 → 363,1 |
| core/COADM01C.cbl | 474 | 430,9 → 373,2 | 13,4% | 1,88 → 1,79 | 5,87 → 5,43 | 316,4 → 298,6 |
| core/COBIL00C.cbl | 375 | 848,3 → 780,5 | 8,0% | 2,33 → 2,27 | 6,42 → 6,34 | 351,0 → 355,2 |
| core/COBSWAIT.cbl | 2 | 125,7 → 117,0 | 6,9% | 1,05 → 1,06 | 3,22 → 3,02 | 218,5 → 205,8 |
| core/COCRDLIC.cbl | 900 | 1401,3 → 1282,3 | 8,5% | 3,31 → 3,24 | 8,50 → 8,65 | 361,4 → 367,2 |
| core/COCRDSLC.cbl | 530 | 682,7 → 578,4 | 15,3% | 2,31 → 2,20 | 6,56 → 6,39 | 336,1 → 302,1 |
| core/COCRDUPC.cbl | 639 | 1251,7 → 1151,5 | 8,0% | 3,05 → 2,97 | 7,87 → 8,10 | 342,4 → 381,0 |
| core/COMEN01C.cbl | 501 | 535,8 → 438,9 | 18,1% | 2,00 → 1,88 | 5,86 → 5,75 | 302,1 → 318,6 |
| core/CORPT00C.cbl | 485 | 3887,2 → 3777,7 | 2,8% | 5,46 → 5,39 | 9,83 → 9,69 | 340,7 → 342,5 |
| core/COSGN00C.cbl | 338 | 418,3 → 345,7 | 17,4% | 1,75 → 1,68 | 5,18 → 5,27 | 289,0 → 303,0 |
| core/COTRN00C.cbl | 940 | 1092,0 → 984,0 | 9,9% | 2,93 → 2,80 | 7,76 → 7,35 | 356,8 → 372,7 |
| core/COTRN01C.cbl | 478 | 622,4 → 577,1 | 7,3% | 2,03 → 2,05 | 5,84 → 6,07 | 299,5 → 320,7 |
| core/COTRN02C.cbl | 514 | 895,9 → 832,6 | 7,1% | 2,51 → 2,49 | 6,52 → 6,64 | 330,4 → 330,7 |
| core/COUSR00C.cbl | 939 | 1063,3 → 995,9 | 6,3% | 2,84 → 2,77 | 7,21 → 7,17 | 356,3 → 356,8 |
| core/COUSR01C.cbl | 348 | 483,0 → 463,0 | 4,1% | 1,88 → 1,83 | 5,63 → 5,03 | 308,7 → 286,2 |
| core/COUSR02C.cbl | 360 | 721,0 → 669,7 | 7,1% | 2,16 → 2,07 | 5,55 → 5,48 | 321,2 → 293,2 |
| core/COUSR03C.cbl | 348 | 561,3 → 500,6 | 10,8% | 1,95 → 1,90 | 5,23 → 5,37 | 290,1 → 309,8 |
| core/CSUTLDTC.cbl | 48 | 258,6 → 231,1 | 10,6% | 1,33 → 1,30 | 3,62 → 3,58 | 234,3 → 235,1 |
| unikix/CBACT01C.cbl | 36 | 277,2 → 251,8 | 9,1% | 1,41 → 1,45 | 4,04 → 4,11 | 255,0 → 256,9 |
| unikix/CBACT02C.cbl | 30 | 248,4 → 252,6 | -1,7% | 1,39 → 1,41 | 3,95 → 4,01 | 257,7 → 257,4 |
| unikix/CBACT03C.cbl | 27 | 254,7 → 254,2 | 0,2% | 1,37 → 1,37 | 3,73 → 3,93 | 235,9 → 256,4 |
| unikix/CBACT04C.cbl | 138 | 499,7 → 482,1 | 3,5% | 1,87 → 1,87 | 5,06 → 5,18 | 292,3 → 281,9 |
| unikix/CBCUS01C.cbl | 42 | 272,6 → 240,3 | 11,9% | 1,42 → 1,38 | 4,05 → 3,89 | 255,9 → 254,5 |
| unikix/CBSTM03A.cbl | 224 | 426,9 → 376,6 | 11,8% | 2,04 → 2,00 | 5,91 → 6,09 | 307,7 → 321,4 |
| unikix/CBSTM03B.cbl | 39 | 227,0 → 206,3 | 9,1% | 1,32 → 1,32 | 3,57 → 3,80 | 235,5 → 256,3 |
| unikix/CBTRN01C.cbl | 132 | 392,9 → 384,2 | 2,2% | 1,69 → 1,72 | 4,71 → 4,85 | 281,5 → 277,5 |
| unikix/CBTRN02C.cbl | 148 | 478,6 → 423,2 | 11,6% | 1,93 → 1,83 | 5,63 → 5,32 | 306,8 → 284,4 |
| unikix/CBTRN03C.cbl | 139 | 631,5 → 558,9 | 11,5% | 2,07 → 1,97 | 5,62 → 5,31 | 291,9 → 292,1 |
| unikix/COACTUPC.cl2 | 1562 | 2543,4 → 2269,9 | 10,8% | 5,31 → 5,04 | 11,09 → 10,42 | 376,4 → 376,8 |
| unikix/COACTVWC.cl2 | 815 | 1397,0 → 1327,2 | 5,0% | 3,24 → 3,15 | 6,64 → 6,51 | 319,3 → 325,3 |
| unikix/COADM01C.cl2 | 468 | 437,8 → 387,2 | 11,5% | 1,88 → 1,77 | 5,43 → 4,97 | 313,1 → 290,3 |
| unikix/COBIL00C.cl2 | 375 | 829,9 → 785,7 | 5,3% | 2,29 → 2,23 | 5,91 → 5,21 | 346,0 → 324,0 |
| unikix/COCRDLIC.cl2 | 900 | 1403,8 → 1281,3 | 8,7% | 3,41 → 3,18 | 8,04 → 7,99 | 329,5 → 352,4 |
| unikix/COCRDSLC.cl2 | 530 | 670,5 → 587,5 | 12,4% | 2,32 → 2,21 | 6,14 → 5,45 | 325,8 → 290,2 |
| unikix/COCRDUPC.cl2 | 639 | 1228,9 → 1127,1 | 8,3% | 3,02 → 2,97 | 6,78 → 7,68 | 329,2 → 355,0 |
| unikix/COMEN01C.cl2 | 497 | 540,7 → 476,8 | 11,8% | 2,42 → 2,25 | 6,96 → 6,45 | 312,6 → 290,6 |
| unikix/CORPT00C.cl2 | 485 | 4097,0 → 3827,0 | 6,6% | 5,85 → 5,46 | 10,03 → 9,33 | 337,6 → 325,8 |
| unikix/COSGN00C.cl2 | 338 | 412,7 → 348,8 | 15,5% | 1,70 → 1,69 | 4,79 → 4,90 | 282,8 → 300,2 |
| unikix/COTRN00C.cl2 | 940 | 1120,6 → 1084,4 | 3,2% | 2,93 → 2,90 | 7,20 → 7,33 | 343,1 → 372,8 |
| unikix/COTRN01C.cl2 | 478 | 621,2 → 564,4 | 9,1% | 2,08 → 2,00 | 5,11 → 5,40 | 298,2 → 294,3 |
| unikix/COTRN02C.cl2 | 514 | 906,5 → 897,4 | 1,0% | 2,63 → 2,70 | 6,04 → 6,42 | 320,6 → 320,2 |
| unikix/COUSR00C.cl2 | 939 | 1154,2 → 1097,8 | 4,9% | 3,21 → 3,14 | 7,38 → 7,29 | 349,9 → 332,2 |
| unikix/COUSR01C.cl2 | 348 | 486,7 → 449,8 | 7,6% | 1,91 → 1,83 | 5,25 → 5,19 | 295,9 → 300,0 |
| unikix/COUSR02C.cl2 | 360 | 686,5 → 665,6 | 3,0% | 2,09 → 2,09 | 5,55 → 5,42 | 323,3 → 292,4 |
| unikix/COUSR03C.cl2 | 348 | 567,2 → 496,1 | 12,5% | 1,96 → 1,90 | 5,25 → 5,72 | 290,0 → 313,1 |
| unikix/CSUTLDTC.cbl | 51 | 267,1 → 241,8 | 9,5% | 1,38 → 1,37 | 3,92 → 4,21 | 260,3 → 262,5 |
| unikix/SDSF.cbl | 0 | 68,8 → 64,6 | 6,1% | 0,93 → 0,94 | 2,86 → 2,69 | 189,8 → 187,3 |

## Equivalência e validação

- Gate estrutural sobre os 73 fontes preprocessados: **73 PASS**, zero fallback, mesma AST completa, origens sintáticas, coverage e diagnósticos. Esse gate não usa whitelist na implementação.
- CLI completa sobre os 73 fontes originais: **73 admitidos, zero fallback e 146 exit codes zero**, com preprocessing/COPY real e publicação dos 19 produtos.
- **1387 pares de arquivos** comparados; **1386 byte-idênticos**. **1 divergência(s) bruta(s)** de serialização JSON listadas abaixo; zero diferença de conteúdo. As divergências não foram apagadas ou tratadas como igualdade bruta.
- FAST local: PASS. Suíte Maven completa: **1.317 testes, zero falhas/erros, um skip histórico**. A qualificação local executou a regressão de normalização e parou no naming preexistente de `docs/work/index.md`, `docs/work/json-zstd.yaml` e `docs/work/post-antlr-performance.md`; esses arquivos não foram alterados.
- Regressão diferencial de fixtures: 308 comparadas, 240 com caminho direto; 23 rejeições de normalização anteriores ao parser e uma falha preexistente reproduzida nos dois modos continuam discriminadas.
- Gaps semânticos e estados parciais do frontend permanecem nos produtos. Equivalência não os promove a análise completa.

- `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl` / `symbol-data.js`: `JSON_SERIALIZATION_ORDER_OR_WHITESPACE`. A verificação suplementar compara JSON sem alterar listas, valores ou tipos serializados.

## Evidência e reprodução

Evidência bruta na raiz agregadora local: `artefatos-e2e/direct-ast-complete-20261002/`.

- `admission.json`: 73 fontes, 146 execuções, nenhuma falha/fallback.
- `programs.csv` (73 linhas), `measurements.csv` (146 linhas), `summary.json`, seleção, manifesto e verificação de integridade.
- `runs.jsonl` e `runs/`: comandos, exit codes, tempos, logs e produtos sem reescrita de baselines.
- `run.py`, `summarize.py` e `report.py`: execução, comparação e relatório.

O teste estrutural local é `io.github.gustavo2358.cobolexplorer.DirectDataCorpusCheck`,
executado no classpath de testes com dois argumentos: manifesto JSON não vazio de
caminhos de fontes preprocessados e diretório de saída. Ele falha se qualquer
entrada cair em fallback ou diferir em AST, origens, coverage ou diagnósticos.
A comparação CLI complementar usa os fontes originais e os copybooks reais.

FAST remoto do código medido: [PASS](https://github.com/Gustavo2358/proleap-poc/actions/runs/37036576945).
Nenhuma pipeline AIR/CFG foi reexecutada: os produtos de entrada desses consumidores
foram comparados integralmente e os contratos não mudaram. A implementação continua
na branch `lab/direct-ast-parser`, [PR #82](https://github.com/Gustavo2358/proleap-poc/pull/82), sem merge.
