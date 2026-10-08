# Parser próprio — corpus completo, rodada única

**O caminho novo foi usado em 3 de 73 programas (4,1%); 70 retornaram ao ANTLR (95,9%).** A meta de redução de 50% foi observada nos dois programas grandes admitidos. O alcance do parser continua pequeno: fallback é a principal limitação deste laboratório.

## O que foi executado

- CardDemo completo: 44 fontes de `app/` e 29 variantes UniKix, incluindo `.cl2`. Inventário verificado no disco: nenhum fonte omitido. COPY/BMS/DCL são dependências, não programas adicionais.
- Uma rodada por modo: 73 × 2 = **146 execuções** no corpus principal. JVMs novas e sequenciais.
- Verificação complementar: 13 arquivos do checkout × 2 = **26 execuções**, incluindo versões/duplicatas dos programas acima. Total selecionado: **172 execuções**. O complemento não entra nos agregados do corpus principal.
- Todas as 172 execuções terminaram com exit code zero. O corpus principal não teve erros de parser nem COPY não resolvido. No complemento, `corpus/carddemo/cbl/COTRTLIC.cbl` apresentou erros de parser nos dois modos.
- Todos os programas publicaram estado degradado por gaps semânticos existentes. Sucesso da CLI e equivalência não significam análise semanticamente completa.
- A pedido do usuário, a análise de desempenho abaixo considera somente os programas admitidos pelo caminho novo. Os tempos brutos dos fallbacks permanecem no CSV de auditoria.

## Desempenho — somente caminho próprio

Valores são **uma observação por modo**, sem média de repetições, estimativa de variância ou significância estatística. Setas: ANTLR → direto.

| Programa | Declarações diretas | Parsing + índice + AST (ms) | Redução | CLI completa (s) | Redução CLI |
| --- | ---: | ---: | ---: | ---: | ---: |
| vsam-mq/COACCT01.cbl | 2582 | 1439,8 → 624,8 | 56,6% | 3,54 → 2,75 | 22,3% |
| vsam-mq/CODATE01.cbl | 2539 | 1382,6 → 556,3 | 59,8% | 3,41 → 2,56 | 24,9% |
| core/COBSWAIT.cbl | 2 | 127,9 → 108,9 | 14,9% | 1,06 → 1,04 | 1,9% |

| Programa | Reconhecimento (ms) | Indexação (ms) | AST (ms) | CPU total (s) | Pico RSS (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: |
| vsam-mq/COACCT01.cbl | 1189,0 → 417,0 | 49,3 → 28,6 | 201,6 → 179,2 | 7,68 → 6,71 | 420,2 → 333,7 |
| vsam-mq/CODATE01.cbl | 1137,0 → 350,7 | 46,6 → 29,4 | 199,1 → 176,1 | 7,58 → 7,39 | 440,5 → 331,9 |
| core/COBSWAIT.cbl | 76,1 → 60,6 | 1,8 → 1,4 | 50,1 → 46,9 | 2,94 → 2,84 | 209,1 → 203,2 |

Somando apenas esses três programas: parsing + índice + AST **2,950 → 1,290 s (−56,3%)**; CLI **8,01 → 6,35 s (−20,7%)**; CPU **18,20 → 16,94 s (−6,9%)**. RSS é pico por processo; não se somam picos de memória como consumo total.

COACCT01 e CODATE01 são dominados por muitas declarações DATA, especialmente PIC. COBSWAIT possui apenas duas declarações: o ganho absoluto combinado foi 19 ms e a CLI caiu 20 ms, insuficiente para uma conclusão estável com uma amostra.

## Fallback — corpus principal

| Primeiro motivo registrado | Programas |
| --- | ---: |
| Seção/fronteira DATA fora do recorte | 27 |
| OCCURS | 41 |
| Nome ambíguo ou ausente | 1 |
| Cláusula após VALUE | 1 |

O motivo é o **primeiro bloqueio encontrado**, não um inventário de tudo que falta naquele programa. Implementar OCCURS não garante admitir os 41 programas: outro bloqueio pode aparecer depois. O fallback descarta os drafts temporários e reprocessa toda a DATA DIVISION pelo ANTLR.

### Inventário dos 73 programas

| Programa | Caminho | Primeiro motivo / declarações |
| --- | --- | --- |
| authorization-ims-db2-mq/CBPAUP0C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| authorization-ims-db2-mq/COPAUA0C.cbl | Fallback | OCCURS |
| authorization-ims-db2-mq/COPAUS0C.cbl | Fallback | OCCURS |
| authorization-ims-db2-mq/COPAUS1C.cbl | Fallback | Nome ambíguo ou ausente |
| authorization-ims-db2-mq/COPAUS2C.cbl | Fallback | OCCURS |
| authorization-ims-db2-mq/DBUNLDGS.CBL | Fallback | Cláusula após VALUE |
| authorization-ims-db2-mq/PAUDBLOD.CBL | Fallback | Seção/fronteira DATA fora do recorte |
| authorization-ims-db2-mq/PAUDBUNL.CBL | Fallback | Seção/fronteira DATA fora do recorte |
| transaction-type-db2/COBTUPDT.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| transaction-type-db2/COTRTLIC.cbl | Fallback | OCCURS |
| transaction-type-db2/COTRTUPC.cbl | Fallback | OCCURS |
| vsam-mq/COACCT01.cbl | Próprio | 2582 declarações |
| vsam-mq/CODATE01.cbl | Próprio | 2539 declarações |
| core/CBACT01C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBACT02C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBACT03C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBACT04C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBCUS01C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBEXPORT.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBIMPORT.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBSTM03A.CBL | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBSTM03B.CBL | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBTRN01C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBTRN02C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/CBTRN03C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| core/COACTUPC.cbl | Fallback | OCCURS |
| core/COACTVWC.cbl | Fallback | OCCURS |
| core/COADM01C.cbl | Fallback | OCCURS |
| core/COBIL00C.cbl | Fallback | OCCURS |
| core/COBSWAIT.cbl | Próprio | 2 declarações |
| core/COCRDLIC.cbl | Fallback | OCCURS |
| core/COCRDSLC.cbl | Fallback | OCCURS |
| core/COCRDUPC.cbl | Fallback | OCCURS |
| core/COMEN01C.cbl | Fallback | OCCURS |
| core/CORPT00C.cbl | Fallback | OCCURS |
| core/COSGN00C.cbl | Fallback | OCCURS |
| core/COTRN00C.cbl | Fallback | OCCURS |
| core/COTRN01C.cbl | Fallback | OCCURS |
| core/COTRN02C.cbl | Fallback | OCCURS |
| core/COUSR00C.cbl | Fallback | OCCURS |
| core/COUSR01C.cbl | Fallback | OCCURS |
| core/COUSR02C.cbl | Fallback | OCCURS |
| core/COUSR03C.cbl | Fallback | OCCURS |
| core/CSUTLDTC.cbl | Fallback | OCCURS |
| unikix/CBACT01C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBACT02C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBACT03C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBACT04C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBCUS01C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBSTM03A.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBSTM03B.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBTRN01C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBTRN02C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/CBTRN03C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| unikix/COACTUPC.cl2 | Fallback | OCCURS |
| unikix/COACTVWC.cl2 | Fallback | OCCURS |
| unikix/COADM01C.cl2 | Fallback | OCCURS |
| unikix/COBIL00C.cl2 | Fallback | OCCURS |
| unikix/COCRDLIC.cl2 | Fallback | OCCURS |
| unikix/COCRDSLC.cl2 | Fallback | OCCURS |
| unikix/COCRDUPC.cl2 | Fallback | OCCURS |
| unikix/COMEN01C.cl2 | Fallback | OCCURS |
| unikix/CORPT00C.cl2 | Fallback | OCCURS |
| unikix/COSGN00C.cl2 | Fallback | OCCURS |
| unikix/COTRN00C.cl2 | Fallback | OCCURS |
| unikix/COTRN01C.cl2 | Fallback | OCCURS |
| unikix/COTRN02C.cl2 | Fallback | OCCURS |
| unikix/COUSR00C.cl2 | Fallback | OCCURS |
| unikix/COUSR01C.cl2 | Fallback | OCCURS |
| unikix/COUSR02C.cl2 | Fallback | OCCURS |
| unikix/COUSR03C.cl2 | Fallback | OCCURS |
| unikix/CSUTLDTC.cbl | Fallback | OCCURS |
| unikix/SDSF.cbl | Fallback | Seção/fronteira DATA fora do recorte |

## Complemento — 13 fontes do checkout

Só COACCT01 usou o caminho próprio: **1.501,9 → 613,8 ms (−59,1%)**, CLI **3,60 → 2,68 s**. É outra entrada de corpus do mesmo programa; não conta como um quarto programa independente nem como repetição estatística do conjunto principal. Os outros 12 caíram em fallback.

| Arquivo | Caminho | Primeiro motivo / declarações |
| --- | --- | --- |
| corpus/carddemo/cbl/CBACT01C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| corpus/carddemo/cbl/CBPAUP0C.cbl | Fallback | Seção/fronteira DATA fora do recorte |
| corpus/carddemo/cbl/CBSTM03A.CBL | Fallback | Seção/fronteira DATA fora do recorte |
| corpus/carddemo/cbl/COACCT01.cbl | Próprio | 2582 declarações |
| corpus/carddemo/cbl/COACTUPC.cbl | Fallback | OCCURS |
| corpus/carddemo/cbl/COCRDSLC.cbl | Fallback | OCCURS |
| corpus/carddemo/cbl/COPAUS1C.cbl | Fallback | Nome ambíguo ou ausente |
| corpus/carddemo/cbl/COTRTLIC.cbl | Fallback | OCCURS |
| corpus/carddemo/cbl/COTRTUPC.cbl | Fallback | OCCURS |
| corpus/carddemo/cbl/COUSR01C.cbl | Fallback | OCCURS |
| corpus/cbl/CBSTM03A.CBL | Fallback | Seção/fronteira DATA fora do recorte |
| corpus/cbl/CBSTM03D.CBL | Fallback | Seção/fronteira DATA fora do recorte |
| corpus/cbl/COACTUPC.cbl | Fallback | OCCURS |

## Comparação dos produtos

- 86 pares de execução × 19 produtos = **1.634 pares de arquivos**. **1.633 idênticos byte a byte**.
- A diferença bruta está em `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl`, arquivo `symbol-data.js`: a ordem de `relation` e `visibility` em um objeto INDEX_NAME foi invertida. O JSON desserializado é igual, preservando listas, valores e tipos serializados. O caminho desse programa foi fallback.
- As 19 saídas de cada caso admitido pelo parser próprio são byte-idênticas. AST, Semantic Product e os demais produtos comparados não tiveram diferenças de conteúdo.
- A divergência não foi apagada dos logs nem tratada como igualdade bruta. A comparação estrutural é suplementar. A construção desse mapa usa `Map.of` no código compartilhado de `SymbolTableBuilder`, sem alteração nesta branch; as execuções extras disponíveis não reproduziram diferença dentro do mesmo modo. Não se atribui causalidade ao parser com base nessa amostra.
- O frontend medido, as dependências, os fontes e os copybooks permaneceram com os mesmos hashes.

## Método e reprodução

- Frontend: `4a85db8de97cc5075ac2c1285d6f415ec47c72ff`, branch `lab/direct-ast-parser`, [PR #82](https://github.com/imd/proleap-poc/pull/82). Nenhuma mudança produtiva nesta medição.
- CardDemo: `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`. Seleção e ordem de copybooks por variante reutilizadas da campanha integral anterior; hashes dos 73 fontes conferidos.
- AMD Ryzen 5 5600GT; Temurin 25.0.4; G1; `-Xms256m -Xmx2g`; DEBUG nos dois modos; JSON sem compressão. Ordem ANTLR/direto alternada por programa. Sem builds ou JVMs da medição concorrentes.
- A métrica principal inclui reconhecimento, indexação de origens sintáticas e construção da AST. Preprocessing, lexer, análises semânticas e exportação ficam fora dessa métrica nos dois modos, mas dentro do tempo de CLI. CPU é user + system e pode superar o tempo de parede por uso de múltiplas threads.
- Houve redução solicitada de três para duas e depois uma rodada. Preservados os registros originais. **20 execuções concluídas da segunda rodada foram excluídas dos tempos**; duas execuções interrompidas estão em `interrupted/`, também excluídas. O manifesto inicial registra o planejamento inicial, e os arquivos de ajuste registram as mudanças. Nenhuma terceira rodada foi executada.
- `run.py` agora reproduz uma rodada por modo. Em um diretório novo, ele fixa o runtime e gera os registros; o diretório atual contém evidência imutável da execução. `summarize.py` seleciona apenas `trial=0` e mantém as divergências brutas.

## Evidência preservada

Na raiz agregadora local, `artefatos-e2e/direct-ast-corpus-20261002/` contém:

- `REPORT.md`, `programs.csv` (86 linhas), `direct-programs.csv` e `measurements.csv` (172 linhas).
- `summary.json`, seleção exata, manifesto inicial, ajustes de repetição e verificação de integridade.
- `runs.jsonl`, comandos, logs e produtos brutos de todas as execuções, inclusive extras excluídas e interrupções identificadas separadamente.
- `run.py`, `summarize.py` e `report.py` para reprodução e auditoria.

Os tempos publicados acima pertencem ao SHA fixado; esta atualização altera somente documentação. FAST e qualificação anteriores não foram reexecutados localmente, pois o código e o build medidos são os mesmos. Foi executado `lean.py docs` para esta atualização.

## Conclusão

Há ganho de mais de 50% na etapa sintática dos dois casos grandes que o parser próprio admite. O resultado não se estende ao corpus inteiro: **95,9% dos programas ainda retornam ao ANTLR**. A próxima decisão é ampliar a admissão (OCCURS e seções DATA), mantendo a comparação diferencial. Esta rodada não demonstra redução geral de 50% na aplicação.
