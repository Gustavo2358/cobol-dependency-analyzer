# Candidatos condicionais de dependência

Implementação aprovada em 26/09/2026. Revisão e integração são registradas nos PRs
listados na validação abaixo.

## Requisito de produto

A falta de certeza abre a incerteza; não elimina um nome sustentado pelo código.
O inventário serve à descoberta de dependências possíveis. Provas de alocação,
independência e completude pertencem às análises que precisam delas, e não são
uma condição geral para publicar um candidato. Uma aproximação não pode ser
apresentada como certeza. Informação que demonstra uma sobrescrita ou uma
condição incompatível continua eliminando o candidato naquela análise.

## Fronteira e regra

O frontend publica fatos nominais de texto (declarações, valores iniciais,
atribuições, comparações e operandos de consulta) obtidos da AST e do binding.
Esses fatos não afirmam alocação independente, ausência de aliases ou execução.
O lower traduz os fatos e reutiliza o grafo source/state R7 já publicado. Não
inventa uma AIR nem amplia os successors do CFG para resolver uma dependência.
O TargetResolver consulta um provider de candidatos condicionais quando não há
uma consulta executável suficiente. A ocorrência e o inventário permanecem únicos.

O provider propaga valores nominais na ordem do grafo publicado, preservando o
snapshot de cada cópia, sobrescritas e filtros de comparação. Texto usa o mesmo
domínio lógico já usado pelo solver executável. Cada candidato condicional
carrega origem, passos de atribuição e premissas abertas de memória, entrada e
interferência. Missing COPY/INCLUDE é preservado com identidade e origem.
Remainder permanece aberto; não é usado como substituto das premissas por candidato.
Fatos ausentes não criam nomes. Não há tratamento por fixture, programa ou alvo.

## Algoritmo, limites e validação

Análise finita por worklist sobre os nodes/derivations do certificado source/state.
As alternativas de chegada são união; uma derivação com callerPremise exige os
dois nós. Atribuições avaliam a origem antes de escrever o destino. Condições
conhecidas filtram candidatos por valor; desconhecidas mantêm alternativas e
incerteza. Ciclos convergem num domínio de valores com limite explícito; corte
retém os candidatos encontrados e informa remainder, nunca assume completude.
A análise é compartilhada por unidade e consultas equivalentes são reutilizadas.
Complexidade depende de nodes, derivations, símbolos demandados e valores retidos;
nenhuma enumeração de caminhos completos é necessária.

A interpretação nominal é uma hipótese identificada. Sobreposição, chamadas
externas, entrada não inicial e operações não modeladas impedem confirmação.
Quando o modelo executável admite a consulta, ele tem prioridade. Novas informações
podem confirmar ou refutar as premissas e retirar candidatos incompatíveis.

Oráculos: literal → cópia → chamada com COPY ausente; sobrescrita; snapshot;
comparação com literal e com outro campo; alias/entrada externos explícitos;
referências inválidas rejeitadas nos dois lados; repetição determinística.
Integração: 47 relações comprovadas, 74 relações baseline preservadas, cinco
falsos positivos conhecidos ausentes; demais novidades classificadas.
Gates: testes focais, admissão/round-trip dos contratos, FAST dos produtos alterados,
corpus completo (a mudança alcança a publicação de todos os targets computados).
AIR/modelo/codec e builder CFG permanecem fora da mudança semântica desta etapa.

## Autoridade

- Requisito explícito do responsável pelo produto nesta sessão: priorizar recall
  e publicar indícios com incerteza; a exigência universal de prova foi scope creep.
- [IBM: MOVE](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statements-move-statement).
- [IBM: elementary moves](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-elementary-moves).
- [IBM: alphanumeric comparisons](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=conditions-alphanumeric-comparisons).
- Contratos locais de ControlTopology, R7/source-state, qualified-source-dependencies
  e resolução unificada. Controle e valores continuam com autoridades separadas.

## Validação integrada aprovada em 26/09/2026

No CardDemo, os 47 falsos negativos comprovados foram recuperados: relações entre
programas de 74 para 121, sem perder relações anteriores e sem incluir os cinco
falsos positivos conhecidos do scanner. O resultado mantém as incertezas de fonte
e interpretação. As 19 diferenças adicionais do scanner não são falsos negativos
comprovados e continuam explicitamente separadas.

A reexecução do indep160 publicou PROGC nos modos padrão e físico; o físico
preservou 321 escritas e a origem do valor, a cópia e a chamada. O modo padrão
agora resolve o destino por texto lógico, sem trabalho físico. O PERFORM manteve
35/39 casos originais aprovados e as mesmas quatro limitações: EXIT PARAGRAPH,
EXIT PERFORM, PERFORM de seção e VARYING com AFTER adicional. Os 42 checks
qualificados passaram: 31 de dependências e 11 de preservação de cobertura;
isso não significa 42 dependências resolvidas. Não houve falha de pipeline ou
candidato fora dos permitidos pelos testes. Oráculos e fontes ficaram intactos.

O fechamento altera somente documentação. FAST e corpus dos commits aprovados
são reutilizados porque implementação, contratos, testes e pins permanecem
inalterados. Os merges preservam os commits exatos consumidos pelos locks;
não é necessário substituir um pin validado apenas para apontar ao merge.
Revisão e integração estão registradas nos PRs abaixo; os resultados brutos e
os SHAs permanecem no repositório local de evidências.

[frontend #60](https://github.com/Gustavo2358/proleap-poc/pull/60), [AIR #22](https://github.com/Gustavo2358/air-java/pull/22), [lower #35](https://github.com/Gustavo2358/cobol-lower/pull/35) e [análise #46](https://github.com/Gustavo2358/analysis-cfg/pull/46).
