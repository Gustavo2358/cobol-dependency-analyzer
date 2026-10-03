# Reconciliação de gaps — validação de 2026-10-03

## Resultado

A mesma pipeline concluiu os **73 fontes do CardDemo**: frontend, lowering, CFG e
dependências, **292 etapas com exit 0**. Foram preservados 65 resultados PARTIAL e
8 COMPLETE. A mudança não adiciona modelagem COBOL nem altera produtos semânticos.

| Registros da lista superior de gaps | Total |
|---|---:|
| Originais preservados | 38.824 |
| Superados por provas atuais | 10.774 |
| Parcialmente cobertos, ainda pendentes | 10.995 |
| Abertos nesta avaliação | 17.055 |
| Pendentes (parciais + abertos) | 28.050 |

Os superados se dividem em 5.514 restrições do perfil legado de PERFORM,
3.993 registros de containment, 1.142 comandos NO_OP com controle publicado e
125 diagnósticos de predicado textual. Isso reduz em 27,8% o contador de pendências
dessa lista, sem apagar os diagnósticos originais. Não representa novas dependências
encontradas, fechamento da análise nem a eliminação de todos os contadores legados.

## Regressão

- **FAST: PASS**, 786 testes, zero falhas, erros ou skips.
- **qualification-local: PASS**. Suite Maven: 1.357 testes, zero falhas/erros,
  um skip já condicionado à propriedade `semantic.condition.required` em
  `SemanticConditionContextDiscoveryTest`; a propriedade não foi ativada.
  O gate também concluiu a regressão full do normalizador e verificação de nomes.
- **730 comparações de artefatos**: 727 byte a byte idênticas. Incluem SP primário,
  envelope de compilação, alias, observed-dependencies, fonte preprocessado,
  source artifact, AIR, dependency-input e CFG em todos os 73 casos.
- Nas três diferenças, somente os mapas de métricas de `dependencies.json` mudaram.
  Comparação estrutural de **todo o restante do JSON**, incluindo relações,
  proveniência, suportes, razões, gaps e estado da análise: idêntica em 73/73.
  A lista exata de caminhos diferentes está no JSON de evidência. Nenhum campo
  semântico foi ignorado, reordenado ou normalizado para obter igualdade.
- Auditoria de **38.824 registros originais** e **32.904 pointers de evidência**:
  código, escopo, statement, detalhe e proveniência preservados; pointers existentes,
  não nulos e pertencentes à ocorrência correta. JSON comprimido e dados da UI
  representam a mesma avaliação.
- Navegador: contadores, seleção de unidade, filtros de estado e código, ranking,
  paginação de 61 ocorrências (50 + 11), expansão de provas, busca sem resultados e
  navegação entre páginas. Console sem erros/avisos durante a verificação do painel.

### Deltas de métricas investigados

Os casos são `app/app-transaction-type-db2/cbl/COTRTLIC.cbl`, e os membros
`COTRN00C.cl2` e `COTRN02C.cl2` do ZIP UniKix. Variaram contadores como tentativas
da worklist, joins e máximo de elementos na fila. Os produtos de entrada e todos
os resultados semânticos permaneceram idênticos. Replays adicionais com o runtime
e os inputs originais do baseline também preservaram os resultados semânticos.
Esta frente não altera o solver nem tenta corrigir suas métricas de execução.

## Baseline e reprodução

Frontend base: `c43b1410ac9235d906ccc90392d5f3bf82399ac1` (origin/main atualizado
antes de criar a branch). Os consumidores foram executados com os mesmos snapshots
compilados da pesquisa anterior, para isolar esta mudança:

| Repositório | SHA |
|---|---|
| cobol-lower | `19cf1fe9a59997f48868a51332487417753e6fce` |
| air-java | `7d77330099f46117281fdcbb08304e20d68f5672` |
| analysis-ir | `fc229ef64eadf26c9ca093a544dad2928ae17dc2` |
| analysis-cfg | `ae3b23d9e853f15fff64ebb49fa40be9391670ba` |

CardDemo: `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`, 44 fontes físicos e 29
membros de arquivo, sem deduplicação por nome de programa. Mantidos os mesmos COPY
roots, modo ANTLR e perfil de storage não selecionado da pesquisa. Java 21.0.12.
O scanner não foi reexecutado: esta regressão compara a pipeline antes/depois;
nenhuma implementação ou configuração do scanner mudou.

[Resumo auditável por fonte, hashes e deltas](gap-assessment-carddemo.json).
Os dados brutos e comandos permanecem no workspace agregador em
`.gap-reconciliation-20261003/evidence/`: `run-corpus.py`, `corpus-results.json`,
`verify-corpus.py`, `verification.json`, `baseline-replay.json`, logs dos gates e
`programs/<id>/`. O baseline foi preservado em
`artefatos-e2e/carddemo-scanner-discovery-20261003/`. Builds e outputs reproduzíveis
não integram o PR.

Para repetir os gates no checkout:

```sh
python3 -B scripts/harness/lean.py fast
python3 -B scripts/harness/lean.py qualification-local
```

## Limites

Esta primeira frente reconcilia somente as regras documentadas em
[gap-assessment](../domain/gap-assessment.md). Códigos sem regra permanecem OPEN;
PARTIAL continua pendente. Diagnósticos internos de storage, runtime, entrada e
outros contratos conservam seus inventários próprios. As conclusões de regressão
valem para os fixtures e para este corpus, com os pins e opções registrados acima.
