# Avaliação dos gaps publicados

O frontend publica `semantic-gap-assessment.json.zst` (ou `.json` com
`--json-compression none`) e `gaps.html`. A avaliação lê somente fatos do port;
não executa resolução, lowering, propagação de valores ou parsing adicionais.
O painel é acessível nas páginas já geradas pelo Explorer.

## Escopo e significado dos números

A versão `1.0.0` avalia **cada registro da lista superior `gaps` de cada unidade**
da compilação. Gaps embutidos em storage, entrada, referências e outros contratos
não são somados novamente. Os contadores de resolução nominal e de cobertura AST
continuam representando seus próprios inventários; não são intercambiáveis com
esta lista. Também não representam uma contagem de dependências perdidas.

- `SUPERSEDED`: uma prova atual substitui a restrição histórica **na dimensão
  indicada**, ou publica precisamente a capacidade antes ausente.
- `PARTIAL`: há suporte relevante, mas ele não encerra a obrigação inteira.
- `OPEN`: nenhuma regra admitida encontrou prova substituta. Inclui casos ainda
  não reconciliados; não significa automaticamente “precisa de nova modelagem”.
- `pending = partial + open`; `raw = superseded + pending`.

A lista original, coverage/readiness, `PARTIAL`, contratos e versões do Semantic
Product permanecem intactos. AIR, CFG e dependencies não consomem esta avaliação.
O ranking ordena registros pendentes por código, com desempate lexical; não tenta
medir impacto em dependências nem deduplicar causas diferentes de uma ocorrência.

## Regras da primeira frente

| Diagnóstico | Prova exigida na mesma unidade e ocorrência | Resultado / limite |
|---|---|---|
| `CONTAINMENT_NOT_PROJECTED` / STRUCTURE | ocorrência e região existentes, membership explícito, provas positivas transitivas | Supera apenas o parent/branch legado; não encerra controle ou efeitos. |
| `PERFORM_ISOLATED_PRIMARY_NOT_PROVEN`, `PERFORM_ISOLATED_PRIMARY_FLOW_NOT_PROVEN`, `PERFORM_LINEAR_MOVE_BODY_NOT_PROVEN`, `PERFORM_ORDINARY_INCOMING_NOT_EXCLUDED` / CAPABILITY | binding de invocação, região, outcome LOCAL_INVOKE correspondente e retomada explícita com provas positivas | Supera restrições do perfil isolado/linear substituídas pela composição atual. Não prova o corpo inteiro, os valores, efeitos ou recursão. |
| `PERFORM_RANGE_CONTROL_NOT_PROVEN`, `PERFORM_RESUME_NOT_PROVEN`, `PERFORM_ORDERED_RANGE_NOT_PROVEN`, `PERFORM_PROCEDURE_STRUCTURE_NOT_PROVEN`, `PERFORM_ENDPOINT_NOT_UNIQUE_LOCAL_PARAGRAPH` / CAPABILITY | mesma prova de invocação | Parcial: binding não basta para provar todas as obrigações. |
| `OBSERVED_STATEMENT_UNSUPPORTED`, `OBSERVED_STATEMENT_PARTIAL` / CAPABILITY | efeito NO_OP e outcomes locais positivos, sem UNKNOWN_LOCAL | Supera a ausência genérica de suporte desse comando. Outros gaps permanecem independentes. |
| mesmos códigos OBSERVED | qualquer outro resumo de efeitos, ou NO_OP sem prova de controle | Parcial: resumo não fecha valores, ambiente, operandos desconhecidos ou controle. |
| `CICS_COMMAND_EFFECTS_NOT_MODELED` / CAPABILITY | `hostEffects` explícito | Parcial: permanecem runtime, estado CICS, handlers e requisitos de storage. |
| `MOVE_IDENTITY_NOT_PROVEN`, `SCALAR_WHOLE_ITEM_NOT_PROVEN` / CAPABILITY | atribuição em `nominalValues` | Parcial: não prova identidade física, alias, conversão ou todos os receivers. |
| `CONDITION_SEMANTICS_NOT_AVAILABLE` / CONDITION_SEMANTICS | `textPredicate` validado no IF | Supera apenas a ausência do predicado textual. |
| `IF_OUTSIDE_SIMPLE_PROFILE` / CAPABILITY | `textPredicate` ou condição nominal | Parcial: braços e conclusão do IF permanecem independentes. |
| `CONDITION_SEMANTICS_NOT_AVAILABLE` / CONDITION_SEMANTICS | somente condição nominal | Parcial: não encerra a prova física. |
| demais códigos, dimensões diferentes ou prova ausente | nenhuma substituição automática | Aberto; sem regra por prefixo ou redução global de contadores. |

As regras de controle recusam `PARTIAL_UNKNOWN` e `CONTROL_POSSIBILITY` também
nas dependências transitivas da prova. Uma possibilidade de continuidade de fonte
não vira controle executável. A substituição do perfil antigo de PERFORM está
fundamentada no contrato de [composição e completion](perform-control-completion.md).
Esta frente não adiciona semântica COBOL.

## Rastreabilidade e compatibilidade

Cada linha conserva índice original do gap, unidade, statement, código, scope,
detail e proveniência completa. `evidence.pointer` é um JSON Pointer relativo ao
Semantic Product **daquela unidade** (primary: `cobol-semantic-product`; demais:
`cobol-semantic-compilation.units[i].product`). O pointer seleciona o fato que
contém a prova e sua autoridade; os IDs transitivos continuam no produto original.
Duplicatas são preservadas. Identidades locais de unidades diferentes não se cruzam.

O relatório e o JavaScript local são duas serializações da mesma avaliação. A UI
apenas filtra e apresenta os resultados do produtor; não reclassifica gaps. Textos
e fontes são inseridos com `textContent`. Filtros incluem unidade, estado, código,
arquivo, linha e texto; resultados são paginados, sem truncar a lista.

## Validação

`SemanticGapAssessmentTest` cobre provas ausentes e transitivamente parciais,
variantes de PERFORM, NO_OP versus efeitos abertos, CICS, MOVE nominal, predicado,
containment, códigos novos, dimensões diferentes, duplicatas e isolamento entre
unidades. Verifica também determinismo, pointers resolvíveis e imutabilidade dos
bytes do produto. A suite integra FAST. `ZstdArtifactTest` exige paridade JSON/zstd
para o novo artefato. O resultado do corpus e os gates executados estão no
[relatório de validação](../validation/gap-assessment.md).
