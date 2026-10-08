# Parser completo próprio — CardDemo 73

Estado: PASS de admissão e equivalência no corpus. Laboratório opt-in; não implica equivalência universal da linguagem.

- Implementação medida: `2e604535cff027979df5427e7f7787fdf1db7000`; branch `lab/direct-ast-parser`; [PR #82](https://github.com/imd/proleap-poc/pull/82).
- Fonte: CardDemo `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`; 44 fontes upstream e 29 variantes UniKix. A seleção integral e a ordem de COPY estão preservadas.
- **73/73 unidades completas pelo parser próprio, zero fallback; 146/146 processos com exit code zero.** Lexer e preprocessador preservados, com hashes das classes idênticos ao laboratório anterior.
- Uma execução por programa e modo. JVM fria por processo, ordem AB/BA alternada, G1, heap 256 MiB–2 GiB, mesma JVM e dependências, DEBUG habilitado nos dois modos, JSON sem compressão. Não houve aquecimento ou descarte de amostras.
- Executável congelado antes da rodada; nenhuma compilação durante os tempos. Comandos, ambiente, logs, consumo de CPU, RSS, fontes, COPY e hashes estão preservados. Uma rodada não permite estimar variância estatística.

## Tempos agregados

A métrica principal inclui reconhecimento, registro de origens e construção da AST. Lexer, preprocessamento e exportação ficam fora dessa métrica nos dois modos. A adaptação dos tokens para valores próprios está incluída no reconhecimento.

| Etapa | ANTLR | Próprio | Redução |
|---|---:|---:|---:|
| Reconhecimento | 47.819 s | 2.798 s | 94.15% |
| Origens/indexação | 0.952 s | 0.522 s | 45.15% |
| AST | 8.305 s | 11.310 s | -36.19% |
| **Parsing + origens + AST** | 57.077 s | 14.631 s | 74.37% |
| CLI completa | 166.930 s | 120.100 s | 28.05% |
| CPU (user + system) | 458.950 s | 378.680 s | 17.49% |
| Maior RSS observado | 482.2 MiB | 376.0 MiB | 22.03% |

56 dos 73 programas superaram 50% individualmente. A meta agregada inclui todos os 73 sucessos próprios, sem seleção dos casos mais rápidos.

A construção da AST isolada aumentou de 8,305 s para 11,310 s. No caminho próprio, as vistas tipadas sobre os registros de origem são materializadas nessa fase; o controle ANTLR já construiu seus contexts durante o reconhecimento. A decisão de desempenho usa a soma das fases.

O ganho não é uniforme: 56/73 programas superaram 50%; 72/73 ficaram mais rápidos. `unikix/SDSF.cbl` passou de 66,469 ms para 108,796 ms em parsing + AST, aumento de 63,68% (42,327 ms absolutos). Esse caso está incluído no total; não foi descartado.

## Equivalência e fallback

- 1387 pares de artefatos comparados; **1387 byte a byte idênticos**. 0 diferenças apenas de ordem de chaves JSON/espaços de serialização; **zero diferença de conteúdo**. Arrays mantêm ordem na comparação. Nenhum artefato bruto foi normalizado ou alterado.
- Comparação inclui apresentação sintática, AST, coverage, símbolos, ocorrências/resolução, Semantic Product da unidade e da compilação, dependências observadas e fontes preprocessadas.
- Diagnósticos e gaps semânticos preexistentes continuam publicados. Admissão própria não significa que toda análise de negócio esteja completa.
- O parser próprio inclui as quatro divisions e os leitores de referências/expressões embutidas. As ações semânticas consultam spans próprios em um registro plano, sem contexts, árvores ou visitantes ANTLR.
- A guarda de execução proíbe instanciar o parser ANTLR no caminho próprio. Em falha nativa, nenhuma AST parcial é publicada; o fallback reinicia o parser e construtor legados sobre os tokens originais.
- Testes diferenciais na implementação medida: 308 fixtures comparadas, 196 nativas, 112 em fallback; 23 rejeitadas antes do parser pela normalização FIXED e uma falha preexistente reproduzida nos dois caminhos. Testes adicionais cobrem ambiguidades, Unicode, SQL/CICS, bloqueio arquitetural e fallback com erro de sintaxe/lexer.
- Validação da implementação medida: FAST local e remoto PASS. Suíte completa local: 1.322 testes, zero falhas/erros, um skip preexistente; teste adicional de SQL/CICS/Unicode validado depois. Regressão de normalização PASS. O wrapper `qualification-local` terminou FAIL pelo naming preexistente em três documentos (`docs/work/index.md`, `json-zstd.yaml`, `post-antlr-performance.md`); o gate não foi enfraquecido.

## Correção posterior: fronteira entre parágrafo e SECTION

As fixtures `resolution/procedure-binding.cbl` e `semantic/nominal-references.cbl`
expunham a mesma decisão incorreta: como `paragraph` permite ponto ausente e corpo
vazio, o reconhecedor consumia o nome de uma seção como parágrafo. A correção
consulta `procedureSectionHeader` e seu ponto final antes dessa decisão. Essa
antecipação usa as tabelas e a memoização próprias; não altera gramática, lexer,
preprocessador, ações de AST ou fallback.

- Os dois casos agora exigem `route=native` em teste, com AST, nós/origens,
  contagens, coverage e diagnósticos iguais ao ANTLR. Casos vizinhos cobrem seção
  inicial, após parágrafos, seções vazias consecutivas, segmentos, nomes numéricos,
  palavras admitidas como nomes, comentários e parágrafos sem ponto. Cabeçalhos
  inválidos continuam em fallback e preservam os diagnósticos legados.
- A auditoria original só normalizava os fontes, sem preprocessamento. Depois da
  correção, ela compara 308 fixtures: 198 nativas e 110 em fallback. As 23 rejeições
  de normalização e a falha idêntica no legado permanecem discriminadas.
- Reexecução dos **112 fallbacks originais com preprocessamento: 112/112 nativos,
  zero fallback e equivalência integral**. Os diagnósticos existentes de COPY
  não resolvido em três fixtures permanecem; admissão não implica input completo.
- Regressão funcional posterior pela CLI: **73/73 CardDemo nativos, zero fallback**;
  1.387 artefatos equivalentes ao ANTLR preservado. Destes, 1.386 são byte a byte
  idênticos; um `symbol-data.js` difere apenas na ordem de duas chaves de objeto
  JSON. Conteúdo e ordem dos arrays são iguais; os outputs brutos foram preservados.
  Fontes, COPY e executável permaneceram com hashes iguais durante a execução.
- FAST: 755 testes sem falhas, erros ou skips. Suíte completa: 1.326 testes,
  zero falhas/erros e o mesmo skip preexistente. Regressão de normalização PASS.
  Naquela execução, `qualification-local` terminou FAIL pelo naming dos três
  documentos listados acima. A qualificação final abaixo registra a correção.

Os tempos da tabela são da implementação congelada indicada no início. A correção
posterior não foi objeto de nova medição de desempenho. Evidência da correção em
`.direct-ast-lab/section-fix-20261002/`, fora do Git, separada da rodada medida.

## Qualificação final do PR

A branch integra a main `bdac887`, preservando as suítes do parser e de assinatura
de entrada na lista FAST. A guarda documental permite vocabulário de medição em
Markdown, referências exatas ao repositório em YAML/YML e caminhos de artefatos
históricos. Código, caminhos versionados e identificadores compostos continuam
protegidos. O FAST agora executa a checagem real do repositório antes do Maven;
se ela falha, o harness propaga o erro.

- Naming: 19 testes, incluindo contracasos e execução sem ripgrep, todos PASS.
- Harness: 13 testes de política e propagação de falha, todos PASS.
- FAST integrado: PASS, 761 testes Java em 122 suítes, sem falhas, erros ou skips.
- `qualification-local`: **PASS completo**, incluindo a regressão de normalização
  e a checagem de naming do repositório. Não há dispensa para os três documentos.
- Suíte Maven integrada: 1.332 testes, zero falhas/erros e um oracle futuro opt-in
  fora do perfil padrão. Os três apontamentos documentais não são exceções ignoradas.

A comparação com `30c3a72` confirmou 1.361 classes de parser, lexer e
preprocessamento byte a byte idênticas. A admissão e equivalência de AST já
verificadas no CardDemo são evidência reutilizada dessas etapas; a integração do
projector da main é coberta pelos contratos de assinatura e pela suíte integrada.

Logs desta qualificação ficam em `.direct-ast-lab/naming-fix-20261002/`.
As medições anteriores conservam seus SHAs e resultados originais. O código do
parser, lexer e preprocessador não mudou nesta correção do harness; a atualização
de semântica de assinatura de entrada é a alteração já integrada pela main.

## Fechamento

Fechamento e merge autorizados em 2 de outubro de 2026 no
[PR #82](https://github.com/imd/proleap-poc/pull/82). O registro DONE passa
a valer com o merge e os checks aprovados; Git/GitHub registram o SHA e a data
efetivos. O código qualificado é `66bd4d29cc9876217642666e1db96a9595a25755`.
O commit de fechamento contém somente documentação.

Critérios atendidos:

- 73/73 programas CardDemo pelo caminho próprio, zero fallback e produtos
  equivalentes ao controle ANTLR, incluindo dependências observadas.
- Redução agregada medida de 74,37% em reconhecimento + origens + AST; o tempo
  total de CLI caiu 28,05%. A amostra e o SHA medidos permanecem os originais.
- Correção de SECTION com testes obrigatórios e 112/112 fallbacks originais
  admitidos quando submetidos ao preprocessamento existente.
- FAST, suíte completa, regressão de normalização, naming e documentação PASS;
  os dois checks remotos do código qualificado estão aprovados.

Contratos, versões e pins de consumidores não mudam neste PR. As comparações
preservam IDs, provenance, coverage, diagnósticos, Semantic Product e dependências
observadas nos casos qualificados. A atualização do projector de assinatura de
entrada pertence à main integrada e tem contratos próprios na suíte completa.
Lowering, AIR e CFG não foram reexecutados neste fechamento documental; a evidência
de compatibilidade é a fronteira publicada pelo frontend. Nenhum repin downstream
faz parte deste fechamento.

O modo padrão continua `antlr`; `--parser direct-ast-lab` permanece opt-in e mantém
o fallback integral. O resultado não afirma cobertura universal de COBOL nem ganho
mínimo de 50% em cada programa. Gaps e estados parciais existentes continuam
visíveis. Os relatórios anteriores permanecem como história, com seus resultados
originais, inclusive falhas já corrigidas.

## Evidência e reprodução

Dados por execução estão em [CSV das 146 medições](direct-parser-full-measurements.csv).
As fontes são fixadas em [CSV dos 73 programas](direct-parser-full-programs.csv).
O conjunto bruto preservado está em
`/home/imd/workspace/teste-e2e/artefatos-e2e/direct-parser-full-20261002/`:
`run.py`, `summarize.py`, `report.py`, `manifest.json`, `selection.json`,
`integrity.json`, `admission.json`, `unchanged-components.json`, `runs.jsonl`
e `runs/` com todos os comandos, logs, tempos e produtos. O executável medido está
congelado em `.direct-ast-lab/complete-parser-runtime/`, fora do Git.
Os scripts exigem diretórios novos; para reproduzir, use outra pasta de saída e
o mesmo manifesto de fontes/COPY, JVM e dependências. Não sobrescreva evidência.

O lexer COBOL, lexer/parser do preprocessador e PreprocessorEngine têm classes
byte a byte idênticas ao executável DATA anterior. A única dependência ANTLR do
parser próprio é a fronteira de entrada do lexer preservado; tokens internos,
reconhecimento, origens, ações semânticas e leitores embutidos são próprios.

## Resultado por programa

Uma amostra por célula; tempos em milissegundos. A coluna CLI mostra segundos da
execução completa no caminho próprio. Detalhes do controle, CPU e RSS estão no CSV.

| Programa | Parsing + AST ANTLR (ms) | Próprio (ms) | Redução | CLI própria (s) |
|---|---:|---:|---:|---:|
| `authorization-ims-db2-mq/CBPAUP0C.cbl` | 333.063 | 139.685 | 58.06% | 1.320 |
| `authorization-ims-db2-mq/COPAUA0C.cbl` | 1598.952 | 386.608 | 75.82% | 2.590 |
| `authorization-ims-db2-mq/COPAUS0C.cbl` | 1415.286 | 247.309 | 82.53% | 2.110 |
| `authorization-ims-db2-mq/COPAUS1C.cbl` | 901.606 | 235.721 | 73.86% | 1.880 |
| `authorization-ims-db2-mq/COPAUS2C.cbl` | 243.032 | 157.833 | 35.06% | 1.330 |
| `authorization-ims-db2-mq/DBUNLDGS.CBL` | 277.494 | 142.477 | 48.66% | 1.340 |
| `authorization-ims-db2-mq/PAUDBLOD.CBL` | 269.536 | 149.631 | 44.49% | 1.360 |
| `authorization-ims-db2-mq/PAUDBUNL.CBL` | 292.500 | 147.107 | 49.71% | 1.320 |
| `transaction-type-db2/COBTUPDT.cbl` | 246.331 | 143.025 | 41.94% | 1.210 |
| `transaction-type-db2/COTRTLIC.cbl` | 1850.968 | 283.868 | 84.66% | 2.260 |
| `transaction-type-db2/COTRTUPC.cbl` | 938.645 | 237.625 | 74.68% | 1.960 |
| `vsam-mq/COACCT01.cbl` | 1443.225 | 335.575 | 76.75% | 2.390 |
| `vsam-mq/CODATE01.cbl` | 1364.024 | 343.613 | 74.81% | 2.300 |
| `core/CBACT01C.cbl` | 341.380 | 148.923 | 56.38% | 1.470 |
| `core/CBACT02C.cbl` | 249.136 | 145.123 | 41.75% | 1.230 |
| `core/CBACT03C.cbl` | 241.301 | 140.972 | 41.58% | 1.180 |
| `core/CBACT04C.cbl` | 479.286 | 158.355 | 66.96% | 1.490 |
| `core/CBCUS01C.cbl` | 288.115 | 137.989 | 52.11% | 1.210 |
| `core/CBEXPORT.cbl` | 307.333 | 153.221 | 50.14% | 1.540 |
| `core/CBIMPORT.cbl` | 317.703 | 154.832 | 51.27% | 1.490 |
| `core/CBSTM03A.CBL` | 417.652 | 190.949 | 54.28% | 1.750 |
| `core/CBSTM03B.CBL` | 226.144 | 137.472 | 39.21% | 1.200 |
| `core/CBTRN01C.cbl` | 397.982 | 147.753 | 62.87% | 1.450 |
| `core/CBTRN02C.cbl` | 451.421 | 157.897 | 65.02% | 1.590 |
| `core/CBTRN03C.cbl` | 603.762 | 164.030 | 72.83% | 1.570 |
| `core/COACTUPC.cbl` | 2677.195 | 343.605 | 87.17% | 3.020 |
| `core/COACTVWC.cbl` | 1369.822 | 233.712 | 82.94% | 1.900 |
| `core/COADM01C.cbl` | 427.557 | 206.676 | 51.66% | 1.540 |
| `core/COBIL00C.cbl` | 802.129 | 229.166 | 71.43% | 1.640 |
| `core/COBSWAIT.cbl` | 122.213 | 116.300 | 4.84% | 1.000 |
| `core/COCRDLIC.cbl` | 1397.491 | 263.736 | 81.13% | 2.110 |
| `core/COCRDSLC.cbl` | 690.797 | 235.580 | 65.90% | 1.780 |
| `core/COCRDUPC.cbl` | 1229.564 | 247.263 | 79.89% | 2.020 |
| `core/COMEN01C.cbl` | 502.368 | 222.247 | 55.76% | 1.620 |
| `core/CORPT00C.cbl` | 3961.723 | 235.321 | 94.06% | 1.750 |
| `core/COSGN00C.cbl` | 397.636 | 186.822 | 53.02% | 1.430 |
| `core/COTRN00C.cbl` | 1059.357 | 236.943 | 77.63% | 1.930 |
| `core/COTRN01C.cbl` | 639.296 | 203.294 | 68.20% | 1.620 |
| `core/COTRN02C.cbl` | 897.966 | 236.034 | 73.71% | 1.770 |
| `core/COUSR00C.cbl` | 1084.802 | 236.050 | 78.24% | 1.930 |
| `core/COUSR01C.cbl` | 475.128 | 187.767 | 60.48% | 1.500 |
| `core/COUSR02C.cbl` | 700.961 | 202.342 | 71.13% | 1.600 |
| `core/COUSR03C.cbl` | 561.637 | 199.551 | 64.47% | 1.540 |
| `core/CSUTLDTC.cbl` | 235.644 | 134.160 | 43.07% | 1.130 |
| `unikix/CBACT01C.cbl` | 253.145 | 136.257 | 46.17% | 1.240 |
| `unikix/CBACT02C.cbl` | 261.244 | 139.985 | 46.42% | 1.230 |
| `unikix/CBACT03C.cbl` | 252.325 | 145.756 | 42.23% | 1.240 |
| `unikix/CBACT04C.cbl` | 461.777 | 156.598 | 66.09% | 1.480 |
| `unikix/CBCUS01C.cbl` | 241.292 | 142.104 | 41.11% | 1.240 |
| `unikix/CBSTM03A.cbl` | 430.354 | 193.511 | 55.03% | 1.750 |
| `unikix/CBSTM03B.cbl` | 214.067 | 134.898 | 36.98% | 1.190 |
| `unikix/CBTRN01C.cbl` | 390.261 | 147.067 | 62.32% | 1.450 |
| `unikix/CBTRN02C.cbl` | 444.191 | 160.216 | 63.93% | 1.550 |
| `unikix/CBTRN03C.cbl` | 579.038 | 159.502 | 72.45% | 1.550 |
| `unikix/COACTUPC.cl2` | 2401.501 | 353.263 | 85.29% | 3.050 |
| `unikix/COACTVWC.cl2` | 1357.195 | 239.568 | 82.35% | 1.920 |
| `unikix/COADM01C.cl2` | 434.509 | 209.713 | 51.74% | 1.570 |
| `unikix/COBIL00C.cl2` | 835.642 | 226.977 | 72.84% | 1.600 |
| `unikix/COCRDLIC.cl2` | 1410.271 | 267.102 | 81.06% | 2.140 |
| `unikix/COCRDSLC.cl2` | 660.824 | 246.078 | 62.76% | 1.750 |
| `unikix/COCRDUPC.cl2` | 1218.771 | 248.800 | 79.59% | 1.990 |
| `unikix/COMEN01C.cl2` | 451.123 | 201.754 | 55.28% | 1.590 |
| `unikix/CORPT00C.cl2` | 3828.940 | 226.417 | 94.09% | 1.760 |
| `unikix/COSGN00C.cl2` | 406.509 | 187.792 | 53.80% | 1.460 |
| `unikix/COTRN00C.cl2` | 1126.439 | 243.749 | 78.36% | 1.980 |
| `unikix/COTRN01C.cl2` | 633.658 | 210.333 | 66.81% | 1.580 |
| `unikix/COTRN02C.cl2` | 897.494 | 245.777 | 72.62% | 1.740 |
| `unikix/COUSR00C.cl2` | 1107.357 | 228.696 | 79.35% | 1.930 |
| `unikix/COUSR01C.cl2` | 485.831 | 189.838 | 60.93% | 1.500 |
| `unikix/COUSR02C.cl2` | 703.417 | 202.529 | 71.21% | 1.570 |
| `unikix/COUSR03C.cl2` | 540.253 | 199.716 | 63.03% | 1.580 |
| `unikix/CSUTLDTC.cbl` | 253.542 | 133.867 | 47.20% | 1.170 |
| `unikix/SDSF.cbl` | 66.469 | 108.796 | -63.68% | 0.930 |

## Decisão

O modo completo atingiu a meta agregada: 57,077 s para 14,631 s, redução de 74,37% em parsing + origens + AST. O tempo total da aplicação é um resultado separado. O parser permanece opt-in enquanto a compatibilidade fora do corpus é ampliada e revisada.
