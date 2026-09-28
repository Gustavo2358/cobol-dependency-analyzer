# CARDDEMO-CONTROL-W3 — controle e memória

Status: IN_PROGRESS. Escopo W3.1–W3.4; recursão fora desta onda.

## Regra e autoridade

- Controle de um CICS_COMMAND com sintaxe suportada é autoridade da ControlTopology. Ausência de binding físico não remove destinos. Lower materializa referências admitidas e mantém MAY conservador para o restante; nenhuma escrita MUST é derivada da lacuna.
- INITIAL reinicializa WORKING-STORAGE, não elimina sua alocação. Não ampliar seeds/invariância inicial nesta mudança. [IBM](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=data-comparison-working-storage-local-storage).
- DECLARE TABLE Db2 documenta esquema e não aloca dados COBOL. Reconhecer sua sintaxe fechada no produtor, antes de provas de storage; INCLUDE realmente ausente permanece input indisponível. [IBM](https://www.ibm.com/docs/en/db2-for-zos/12.0.0?topic=statements-declare-table).
- RECEIVE MAP literal sem INTO/SET usa nome do mapa seguido de I; SEND MAP sem FROM usa O. Essa referência é derivada no bridge AST, resolvida pelo resolver nominal habitual e publicada separadamente das opções escritas. Origem é o MAP literal e nunca afirma correspondência textual exata. Ausência/ambiguidade não vira binding. [IBM RECEIVE](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-receive-map).

## Contrato e algoritmo

SP2.53 acrescenta `implicitArea` opcional a CICS_COMMAND. É uma DataReference canônica owned pelo statement, WRITE para RECEIVE, READ para SEND. Só MAP literal e ausência de área explícita autorizam a prova. Identidade, provenance e testes wire bilaterais; produtos antigos continuam admitidos sem campo. HostEffects permanece prova separada de footprint fechado.

Processamento local linear no número de opções/referências, com resolução nominal já indexada. Reconhecimento DECLARE TABLE usa tokens, literal/identificador delimitado e parênteses balanceados; só metadados documentados fechados deixam de ser região opaca. Layout de inputs desconhecidos permanece aberto. Nenhum novo contrato AIR.

## Oráculos antes da implementação

INITIAL conserva célula local sem promover seed; DECLARE TABLE antes/depois de grupos independentes não tainta alocação; INCLUDE ausente ainda tainta. BMS input/output explícito e implícito, nome dinâmico, ausente, ambíguo e qualificação; não inferir nome downstream. CICS com modelo/memória ausente preserva normal, unknown control continua fronteira, envelope MAY aberto sem MUST. CardDemo 73, storage/alias/entry, FILE/CICS e modelos; perfis separados do baseline.
