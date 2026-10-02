# Compatibilidade de banners REMARKS

- id: REMARKS-AREA-A-001
- title: Aceitar documentação com asterisco na Area A dentro de REMARKS
- status: IN_PROGRESS
- scope: normalizador fixed-format, contratos focais, documentação e avaliação E2E

## Conclusão

A extensão delimitada é viável para o fixture reconstruído das fotos. O bloqueio
era causado pela fronteira estrita de Area A do normalizador. Manter os banners
como documentação permite publicar Semantic Product, AIR, CFG e dependências.
A implementação está em branch de avaliação, pendente de revisão e integração.
O mesmo PR inclui a [preservação após controle CICS parcial](cics-source-preservation.md);
os sete controles de REMARKS foram repetidos com essa correção e mantiveram o
JSON de dependências inteiro.

O contrato está em [source format](../domain/source-format-and-normalization.md#compatibilidade-de-banners-em-remarks)
e no [ADR-0001](../architecture/decisions/0001-comment-entry-normalization.md).
Só uma entrada REMARKS já aberta habilita a extensão. O primeiro caractere deve
ser `*` nas colunas 8–11, excluindo `*>`. Outros itens na Area A encerram o estado.
Texto comum na Area A, asteriscos fora de REMARKS e o asterisco na coluna 72 após
COPY permanecem fora desta extensão.

## Contraexemplo que delimitou a regra

A primeira hipótese também absorvia `*>`. Um programa já aceito pelo parser,
com esse marcador na Area A seguido de `PROCEDURE DIVISION` na Area B, perdia
seu CALL sem erro de parsing. A implementação final preserva o tratamento
anterior de `*>`. O contraexemplo integra os dez testes de
`RemarksNormalizationCompatibilityTest` e foi executado até dependências.

## Evidência local

Baseline: main `bdac887e2cfeb203b9228ba670ad5593c7335c3c`, com implementação
equivalente ao runtime congelado de `83959c920afadb9d9912ab1f1e1db2b0ebdf45da`.
Campanha local: `artefatos-e2e/remarks-evaluation-20261002/` no workspace agregador.

- RED inicial: 6 falhas esperadas em 9 testes; controles negativos passaram.
- FAST final: 748 testes, zero falhas/erros/skips.
- qualification-local final: PASS; Maven 1319 testes, zero falhas/erros e um
  skip condicional preexistente; regressão completa do normalizador e naming PASS.
- 96 casos até dependências: os 93 produtos antes disponíveis ficaram idênticos
  em todo o JSON; um REMARKS recuperado e duas rejeições preservadas.
- Inventário preservado nesses 93 produtos: 1336 arestas de programas,
  385 de arquivos e 529 relações de dependência de fonte, incluindo supports,
  origens, gaps, estados parciais e metadados.
- Três oráculos com banner no fonte, banner em COPY e SQLCA modelada produziram
  JSON integralmente igual aos controles independentes com comentário na coluna 7.
  CALL literal, dinâmico, vindo de COPY e arquivo permaneceram; documentação
  com CALL/COPY fictícios não gerou dependências. SQLCA manteve PARTIAL.
- A rodada de 96 casos usou a primeira versão. Seu reuso para a versão final foi
  condicionado à igualdade de texto normalizado, todos os segmentos de origem,
  fronteira física, diagnostics e erros nos 224 arquivos físicos usados, em
  INCLUDE e EXCLUDE: 448 comparações idênticas. Consumidores e jars são idênticos.
  Os sete pares focais foram executados novamente no runtime final, incluindo
  o contraexemplo de `*>`.

Pins: lower `01096a2a4b7e103bb679f308c9df7bce34a45dd5`;
air-java `6c4a6eb225fb4bb4fdc5871bc5232f03387ef099`;
CFG/dependências `b3fed8de9994ce154c85191758775dbece09a85a`;
especificação AIR `2c7f31f19efbe3211a2aea5bbda90173a9666fe2`.

## Limites

A evidência sustenta esta política e estes casos; não prova ausência universal
de regressões nem aceitação de todo texto legado. O fixture é uma reconstrução
sintética das fotos. Não executamos compilador IBM nem tivemos os bytes e a JCL
do fonte original do ChangeMan. As regras IBM e a política de importação do
analisador têm propósitos distintos, explicitados no documento de domínio.
