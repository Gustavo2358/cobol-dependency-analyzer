# Prioridade 2 — MOVE, tipos e transferências locais

> Fechamento do frontend: DONE / MERGED no PR #86. [Integração e limites](priority2-integration.md). Os estados de revisão abaixo são históricos.

Implementação qualificada e pronta para revisão. O work item permanece
IN_PROGRESS até merge, conforme o lifecycle do repositório. O relatório CP2.1
é histórico; esta é a qualificação do escopo completo redefinido pela auditoria
causal de produto em 2026-10-04.

## Mudança entregue

SP 2.66 publica scalarNumber, numericTransfers, edição tipada e regras de
fitting, substituindo o recorte de inteiro DISPLAY. Tipagem não concede
LOCAL_CELL: armazenamento, aliases e intervalos precisam de provas próprias.
Os mesmos fatos canônicos atendem literais/DATA, múltiplos receptores,
DISPLAY/PACKED/BINARY/COMP-5, sinal, escala, TRUNC explícito ou desconhecido,
ZERO/LOW/HIGH, edição, grupos e fatias constantes admitidos.

O projector traduz fatos tipados; não reanalisa COBOL. Descritores e receitas
são comprimidos: PICTURE/OCCURS grandes não se tornam vetores por posição.
Provenance aproximada de COPY conserva sua qualificação e não apaga uma prova
local já válida. A ordem dos receptores e as limitações de alias permanecem
explícitas. Não há uma segunda implementação do MOVE inteiro anterior.

Regras, fontes primárias, complexidade e oracles estão em
[domínio MOVE](../domain/numeric-move.md). O inventário dos 73 programas está em
[numeric-move-corpus.md](numeric-move-corpus.md), ordenado por gaps MOVE restantes.

## Qualificação final — 2026-10-04

O corpus congelado contém 73 fontes/variantes CardDemo. As 292 etapas de
frontend, lower, CFG e dependencies passaram. Depois do ajuste apenas no cache
interno de BDD, dependencies foi reexecutado nos 73 inputs: 73/73 semanticamente
idênticos, sendo 71 byte-idênticos e dois com diferenças somente em métricas de
execução. SP, AIR e CFG dessa reexecução foram reutilizados sem alteração.

| Inventário por ocorrência de MOVE | Quantidade |
| --- | ---: |
| Transferência precisa no modelo admitido | 5.450 |
| Transferência abstrata com causa preservada | 236 |
| Fronteira restante de modelagem | 1.329 |
| Código ausente identificado como causa direta | 0 |
| Total | 7.015 |

As 236 ocorrências abstratas produzem 463 Assigns com Unknown, considerando
receptores e contextos. Não resta MOVE_VALUE_NOT_MODELED na AIR deste corpus.
As 1.329 fronteiras se dividem em 1.173 de capacidade MOVE e 156 de controle ou
publicação executável. Esta classificação exige conferir a AIR por origem;
um fato positivo no SP sozinho não conta como transferência executável.

As três classes priorizadas somavam 10.803 diagnósticos. Restam 1.035
MOVE_IDENTITY_NOT_PROVEN e 429 SCALAR_WHOLE_ITEM_NOT_PROVEN;
LITERAL_KIND_NOT_PUBLISHED chegou a zero. São 1.464 diagnósticos, não 1.464
MOVEs. O total de gaps SP caiu de 23.960 após a prioridade 1 para 14.528.
Os resultados conservam 65 PARTIAL / 8 COMPLETE.

## Dependências e controles preservados

- Programas: 150 sites / 208 candidatos; arquivos: 391 / 378;
  fonte qualificada: 259 / 95. COPY/SQL INCLUDE, declarações de arquivo e
  dependências observadas permanecem iguais nos 73 fontes.
- Frente a CP2.1, uma exclusão foi investigada: COSGN00C no XCTL statement:323
  de COPAUS0C. A chamada do parágrafo grava COMEN01C antes do PERFORM; o fallback
  por SPACES/LOW-VALUES não cabe nesse caminho provado. Oracles independentes
  menu/spaces/unknown preservam o fallback nos casos em que ele é possível.
- Frente à wave10, nenhum candidato, site, suporte, qualificação ou remainder
  de valor mudou. Cinco sites passaram a explicitar openControlRemainder:
  MQPUT1/MQCLOSE/MQGET em COPAUA0C, COPAUS2C em COPAUS1C e MVSWAIT em COBSWAIT.
  A causa são fronteiras de representação numérica/textual inválida; os
  candidatos e seus supports permanecem presentes.
- As provas de fonte qualificada conservaram unidades, nós e derivações;
  65 bundles mudaram somente hashes de SP/AIR. Não se exige identidade de
  hash de produtos cujo conteúdo mudou.
- ConditionNames e ControlTopology são idênticos aos de CP2.1 nos 73 fontes.
  Permanecem 3.807 ocorrências de condições 88, incluindo 1.819 SETs.

## Limites explícitos

A conclusão segue o escopo causal finito definido na auditoria de produto de
2026-10-04. Não significa completar a semântica de todo MOVE COBOL. Os resíduos
incluem funções/LENGTH OF (81 ocorrências observadas), receptores ainda sem
prova suficiente (48), RETURN-CODE (9), grupos, representações e acessos sem
modelagem, além das fronteiras de controle. O inventário registra 397 MOVEs
com faceta de storage de catálogo não provado e 16 com referência DIBSTAT
não resolvida; essas facetas não são uma contagem adicional de gaps.

Os 585 COPY e 11 SQL INCLUDE do inventário estão resolvidos nominalmente.
Isso não concede layout físico aos modelos de catálogo nem prova input completo
para qualquer execução. TRUNC/CCSID ausentes não recebem defaults; não há
manifesto externo obrigatório. AP/exporter, slicing interprograma e predicados
da prioridade 3 permanecem fora desta entrega.

## Evidência de validação

FAST frontend final PASS. A fronteira SP foi exercitada pelos testes de
contrato/adversariais e pelo lower real nos 73 fontes. FAST de AIR, lower e CFG
PASS; mudanças finais documentais/pins não alteram o código executado.
O probe COBOL → AIR → StatementEffects/RD verifica três escritas Unknown,
uma leitura de origem, causa explícita, overwrite MUST, vizinho preservado e
zero ramo inventado para TRUNC ausente. Provas de aliases/fatias/edição,
receptores mistos, condições 88/PERFORM e AIR permanecem no FAST correspondente.

A campanha local conserva comandos, exit codes, tempos, produtos e hashes em
`.gap-reconciliation-20261003/priority2-evidence`, diretório irmão dos worktrees.
Entradas finais: final-scope-frontend, final-scope-pipeline,
final-cache-dependencies e final-causal-trunc. O relatório integrado local está
em `artefatos-e2e/priority2-move-20261004/REPORT.md` no workspace agregador.
Logs RED, timeouts e correções permanecem preservados; não são apresentados
como PASS. Nenhum merge foi realizado.

PRs coordenados: frontend #86, lower #58, AIR #27, analysis-ir #10 e CFG #64.
