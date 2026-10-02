# WORK-ENTRY-SIGNATURE-LOCALITY — DONE

## Regra e escopo

Corrigir o bloqueio de retorno compartilhado reproduzido com SQLCA modelada,
entrada primária sem cláusulas e ENTRY USING. INPUT_MISSING descreve o input
global; não invalida a forma escrita de um cabeçalho independente.

Autoridade: o contrato SP publica parameterCount e returningClause como fatos
da assinatura escrita. A [regra IBM de ENTRY](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=statements-entry-statement)
define o início alternativo após a declaração e permite USING; não introduz um
resultado RETURNING. A AIR I-61 exige cobrir todas as entradas que podem executar
o retorno. Não é correto inventar resultado nem enfraquecer seu validador.

## Algoritmo e limites

Reutilizar EntryInputProof, construída na AST por regiões e provenance. Somente
diagnósticos de COPY incompleta inteiramente em DATA ou em outra unidade com
fronteira comprovada podem coexistir com prova da entrada. Erros de lexer/parser,
COPY na assinatura/PROCEDURE e gaps sem localização não recebem essa prova.
Quando a prova é válida, projetar a forma canônica do cabeçalho: sem cláusulas,
zero parâmetros/RETURNING ausente; com cláusulas, contagem/presença preservadas
e assinatura PARTIAL. Tipos, bindings, storage e valores continuam sujeitos aos
seus próprios contratos. Inventário, coverage e gaps de input seguem incompletos.

A projeção continua sem parsing, resolução ou análise nova. O custo usa a mesma
prova de entrada já calculada, com varredura finita dos filhos da divisão.

## Oracles antes da alteração

- SQLCA modelada + ENTRY USING + GOBACK compartilhado conserva ABSENT nas duas entradas.
- COPY ausente em DATA conserva USING/RETURNING escritos e não inventa bindings.
- COPY em header/PROCEDURE e erro sintático conservam assinatura desconhecida.
- Fronteira SP → lower → AIR → CFG: produzir AIR válida e CFG com ambas as entradas,
  mantendo PARTIAL e os gaps de input.
- As reproduções sintéticas de asterisco na coluna 72 e comentário na coluna 8
  conservam a rejeição no analisador; variantes ajustadas são verificadas à parte.
  Isso não é um oráculo de aceitação dos membros originais pelo compilador IBM.

## Resultado local

A implementação preserva a forma escrita somente sob a prova local. Não houve
mudança de schema/versão SP, AIR, lower ou CFG. O caso SQLCA + ENTRY USING passa
pelas três CLIs; ambos os resultados AIR são vazios e fechados, enquanto os
parâmetros alternativos e a cobertura continuam abertos/PARTIAL.

- RED: três falhas esperadas em seis testes novos, antes do patch.
- GREEN focal: 64 testes, zero falhas/erros/skips.
- FAST: 738 testes, zero falhas/erros/skips; política e arquitetura PASS.
- Suíte Maven completa: 1.309 testes, zero falhas/erros, um skip condicionado
  preexistente (`SemanticConditionContextDiscoveryTest`).
- Regressão full do normalizador: PASS, incluindo COACTUPC, comment entry e COPY.
- `qualification-local`: FAIL na última checagem, `verify-naming.sh`, por conteúdo
  preexistente em `docs/work/index.md`, `docs/work/json-zstd.yaml` e
  `docs/work/post-antlr-performance.md`. Os três arquivos e o script estão
  byte-idênticos ao HEAD anterior. Não se declara full PASS.
- E2E: 34 oracles de produtos; assinatura, gaps, ambas as entradas e retorno para
  a saída da ativação correta. Fatos SP fora de entryInventory idênticos no caso
  SQLCA; controles corrigidos preservam SP/AIR/CFG byte a byte.
- Os dois inputs sintéticos de formato continuam rejeitados por ENTRY_START;
  variantes ajustadas atravessam frontend/lower/CFG. Há um negativo adicional
  com banner na coluna 8 antes da identificação, sem programa produzido.

## Produto final de dependências

A validação adicional executou 96 pares BEFORE/AFTER até `dependencies.json.zst`:
73 fontes CardDemo congeladas, nove controles de corpus, seis variantes das fotos
e oito controles ENTRY. Todos os 82 pares de corpus completam as quatro etapas.

- 88 produtos finais comparáveis preservam dependências, candidatos brutos e
  interpretados, suportes, origens, premissas, alcance por entrada, relações de
  arquivo/fonte, status e métricas. 45 JSONs são estruturalmente idênticos; em 43,
  diferem somente identidades de publicação, digests verificados e remoção da
  incerteza de assinatura comprovadamente refinada no AIR.
- Inventário preservado: 1.329 vínculos de programa, 382 de arquivo e 524 relações
  de fonte, com seus suportes. 23 produtos continuam COMPLETE e 65 PARTIAL.
- Cinco casos antes bloqueados completam a pipeline; três negativos sintéticos
  conservam a rejeição. Os recuperados têm oráculos independentes, pois não
  existia produto final anterior para comparar.
- Controles positivos conservam PRIMARY/ALTLIT/ALTDYN, ALTFILE por entrada
  alternativa e SQLCA. Com DATA incompleta, ALTDYN conserva suporte condicional
  de fonte; não se inventa prova executável. Auditoria: 1.807 asserções PASS.

Evidências locais em `artefatos-e2e/three-blockers-20261002/`, no workspace
agregador; relatório adicional em `dependencies-validation/REPORT.md`. O Git de
artefatos registra scripts, relatórios e manifests de hashes. Os produtos brutos
permanecem nos diretórios locais de execução, fora do commit de evidências.

## Limite dos exemplos de formato

Os inputs foram reconstruídos de fotos; não são transcrições byte a byte dos
membros originais. Nenhum compilador IBM foi executado. A aceitação desses membros
na instalação da empresa permanece NÃO VERIFICADA: depende dos registros físicos,
versão, opções, preparação e mensagens/RC da compilação correspondente.

A IBM define texto ativo nas colunas 8–72 e comentário de linha com `*` na coluna 7
ou `*>`. Comment-entry tem texto livre em Area B. O guia de migração distingue
REMARKS aceito em OS/VS COBOL e não suportado como parágrafo em Enterprise COBOL.
Nosso normalizador aceita esse owner legado e encerra a entrada ao encontrar
texto em Area A. Portanto, a rejeição local não demonstra que os membros originais
precisem ser editados; compatibilidade com o ambiente real segue pendente.

Autoridades: [formato fixo IBM](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=structure-reference-format),
[comment entries](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=division-optional-paragraphs),
[REMARKS na migração OS/VS](https://www.ibm.com/docs/en/cobol-zos/6.5.0?topic=programs-language-elements-that-are-not-supported).

## Fechamento

Implementação: `83959c920afadb9d9912ab1f1e1db2b0ebdf45da`.
[PR #83](https://github.com/Gustavo2358/proleap-poc/pull/83) mergeado na main em
2026-10-02, com autorização explícita do usuário e os dois checks FAST verdes.
Merge: `8ef4f0fa83800f9d192f238e9600addd60c52b9d`. O item está DONE pelo contrato
Git/PR/testes do harness. O fechamento cobre a assinatura; não declara resolvida
a compatibilidade IBM dos dois padrões físicos.

Este fechamento altera somente documentação. Testes focais, FAST, suíte Maven,
regressão do normalizador e os 96 pares de dependências são evidência reutilizada
do código acima, sem alteração de sources, testes, contratos, build ou pins.
Não houve novo full nem reexecução do corpus por causa dessa edição documental.
FAST/full dos consumidores não foram repetidos: permanecem inalterados e suas
fronteiras foram exercitadas pelas CLIs reais recompiladas.

A execução histórica de `qualification-local` permanece FAIL no log original.
Os três problemas documentais de nomenclatura foram corrigidos no fechamento;
a verificação correspondente passou no fechamento, sem alterar o script ou
os dados brutos e sem relabelar a execução histórica como full PASS.

Validação nova do fechamento: `lean.py docs` PASS (12 testes de política),
`verify-naming.sh` PASS e diff não documental vazio contra a implementação
validada. O CI remoto do PR documental fica registrado no próprio PR.
