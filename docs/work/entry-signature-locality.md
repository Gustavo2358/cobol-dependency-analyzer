# WORK-ENTRY-SIGNATURE-LOCALITY — IN_PROGRESS

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
- Os exemplos de asterisco na coluna 72 e comentários na coluna 8 permanecem
  negativos; variantes corrigidas respeitam a coluna 7 e são verificadas à parte.

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
- Os dois inputs de formato inválido continuam rejeitados por ENTRY_START;
  fontes sintéticas com as colunas corrigidas atravessam frontend/lower/CFG.

Evidências locais em `artefatos-e2e/three-blockers-20261002/`, no workspace
agregador. Histórico/corpus integral e FAST dos consumidores não repetidos:
consumidores e AIR permanecem inalterados; suas CLIs foram recompiladas e a
fronteira afetada foi exercitada. Revisão humana pendente; sem merge.
