# Resultado do experimento de origens — 2026-10-09

A direção é promissora: o protótipo elimina a enumeração por contexto na família
medida, preserva os candidatos do corpus e termina N=2.048 com heap de 512 MiB.
É investigação, **não uma implementação qualificada para produção**. Predicados,
retornos não provados e posições de tabelas são aproximados; há nomes adicionais.
Não existe prova de preservação para COBOL arbitrário nem promessa de custo linear
universal. O programa corporativo não foi executado nesta sessão.

## Baseline e isolamento

- Base limpa: `52c1b82dc6accbb615818cf5b5298843a85b0f01` (merge PR 5).
- JAR congelado: `6bd96f31911976c097c9fb47377587c11bf3ebe6dc60305190104314bc39b036`.
- Branch: `experiment/value-origin-graph`; nenhuma alteração em `src/` ou `pom.xml`.
- Overlay final: v10; hashes do solver e da agregação conferidos com os fontes.
- Reuso da qualificação da produção: FAST 1.093 testes em 142 suites,
  CardDemo 73/815 e 36 fixtures da execução anterior no HEAD remoto.
  FAST **não foi reexecutado contra o overlay**; não se usa esse reuso para
  qualificar a nova semântica. O overlay tem testes diferenciais próprios.
- Todas as análises sequenciais: Xmx512 MiB, RSS768 MiB, reserva host2 GiB,
  max-work100M, timeout60 s. Compilador128 MiB. Nenhum aumento de heap.

## O mecanismo testado

```text
consulta de uma variável
       |
       v
origens antes da consulta <--- definições/escritas que podem alcançá-la
       |                          |
       |                          +-- constante: encerra a busca anterior
       |                          +-- abertura: origens próprias + UNKNOWN
       |                          +-- transformação: operandos necessários
       v
ciclos de união/cópia condensados em um componente compartilhado
       |
       v
candidatos compartilhados ---> uma enumeração por conjunto/tipo ---> JSON
```

O frontend e as operações locais de valores são reaproveitados. O alcance físico
admite todos os ramos publicados e retomadas especulativas de chamadas. Não há
contextos de invocação, decisão por estado ou solver antigo como fallback. Só os
ciclos de união/cópia são condensados; transformações não são equacionadas com
identidade. BEFORE/AFTER preserva a ordem das escritas e das consultas.

O suporte da saída é essencial: inicializar ou abrir vários campos não torna o
valor antigo de cada destino um operando de todos os outros. Avaliação com TOP
certifica saídas constantes; o suporte local de abertura permite uma equação de
união. Nos demais casos, a operação é compartilhada por posição e operandos.
Textos redefinidos precisam também dos campos irmãos usados na reconstrução.
Essa última dependência é real e foi mantida após um contraexemplo.

Na agregação, consultas com o mesmo conjunto imutável compartilham a enumeração.
A separação por tipo/regra de validação mantém programas e arquivos distintos.
Como cada consulta do grupo tem os mesmos nomes, escolher sua menor proveniência
antes de enumerar preserva a menor proveniência por nome; avisos inválidos CICS
continuam emitidos para cada linha. Só se compartilha identidade efetivamente
igual, sem supor que conjuntos diferentes sejam equivalentes.

## Comparação de desempenho

Três repetições pareadas com o checkpoint v9; todas com JSON idêntico:

| Família | Baseline mediana | Protótipo v9 mediana | RSS mediano antes → depois |
|---|---:|---:|---:|
| Dinâmica N=64 | 11.38 s | 1.21 s | 585 → 165 MiB |
| Literal N=128 | 6.36 s | 1.31 s | 419 → 175 MiB |

Baseline dinâmica N=128: três execuções no checkpoint anterior atingiram a guarda
de 60 s. Esses resultados foram preservados, sem repetir a falha ou aumentar heap.
Portanto, N maiores usam o oráculo sintético de nomes, **não paridade** com uma
baseline que não terminou. V10 corrige operandos de aliases; a família sintética
não tem REDEFINES, e os contadores continuam os mesmos. Seus tempos abaixo são
novas medidas individuais, não medianas nem projeções de speedup.

| N dinâmico | Tempo v10 (s) | RSS (MiB) | Origens | Candidatos em valores distintos | Pares enumerados |
|---:|---:|---:|---:|---:|---:|
| 64 | 1.26 | 164 | 4,118 | 770 | 257 |
| 128 | 1.78 | 221 | 8,214 | 1,538 | 513 |
| 256 | 1.97 | 277 | 16,406 | 3,074 | 1,025 |
| 512 | 2.78 | 396 | 32,790 | 6,146 | 2,049 |
| 1024 | 4.40 | 461 | 65,558 | 12,290 | 4,097 |
| 2048 | 8.00 | 714 | 131,094 | 24,578 | 8,193 |

Todas as escalas retornaram os N+1 nomes esperados, sem nomes adicionais ao
oráculo. N=2.048 tem 46,108 linhas e 8.192 consultas. O trabalho
registrado é `540N+146`, origens `64N+22`, candidatos em valores distintos
`12N+2` e enumerações da saída `4N+1`. Essas relações são observadas/checadas
nesta família. Para N=2.048, a agregação evita percorrer
8,394,752 pares e percorre
8,193. A fase de resolução/agregação final
levou 0.052 s.

Os contadores de controle canônico no JSON de métricas são zero porque esse
solver não roda. A topologia real do protótipo é medida por ORIGIN_PROBE.
`evaluations` conta transformações no ponto fixo; avaliações de independência
são contadas separadamente em ORIGIN_PRUNING.

## Dependências e precisão

- V10: 37 fixtures diferenciais completas, zero candidatos perdidos,
  82 adicionais; 13 JSONs idênticos.
- Quatro fixtures novas: grupos parciais, alias com duas consultas, alias com uma
  consulta e publicação programa/arquivo. Todas com JSON idêntico à baseline.
- CardDemo v10: 73/73 completos, 815 candidatos preservados, zero perdidos,
  11 adicionais; 62 JSONs idênticos e 11 com um nome adicional.
  Os 185 hashes de entrada permaneceram iguais. Pico RSS 284 MiB.
- Diagnostics diferem deliberadamente: o protótipo sempre informa aproximação e
  pode ampliar remainders desconhecidos. Exit1/PARTIAL não é paridade operacional.
- Fixture02 retorna 50 nomes adicionais: a baseline não admite a retomada de
  PERFORMs que não retornam; o experimento admite. Não chamar isso de precisão.

Não se alteraram oráculos para produzir PASS. Os nomes extras incluem retornos
admitidos sem prova, correlação ignorada e elementos de tabelas resumidos. Os
11 extras do CardDemo são registrados caso a caso em `carddemo-v10/results.json`.
A política de aceitar possíveis dependências adicionais precisa fazer parte da
semântica do produto antes de uma substituição do caminho canônico.

## Paredes descobertas e versões descartadas

1. v2 perdeu três nomes de índices conhecidos porque não havia células de tabela.
   v3 passou a ler o resumo da declaração; pode incluir posições adicionais.
2. v4 perdeu SECOND na escrita inteira de tabela. v5 passa a decodificar todos os
   trechos conhecidos no resumo, sem materializar células; FIRST torna-se extra.
3. v5–v7 provocaram OOM no COACTUPC. JFR apontou construção de Origin/Cell/arestas,
   antes do ponto fixo; v7 chegou a mais de 1,9 milhão de origens. Não era concatenação
   de candidatos. v8 restringiu operandos à saída solicitada: cerca de 35 mil origens,
   execução completa e JSON idêntico. O bom resultado sintético sozinho não bastou.
4. v9 eliminou a enumeração repetida consultas×candidatos na agregação.
5. v9 ainda perdeu uma consulta através de REDEFINES. Com duas consultas, o nome
   reaparecia depois, mascarando a perda no conjunto e alterando sua primeira linha.
   A fixture de uma única consulta comprovou MISSING. v10 inclui os irmãos do texto
   redefinido e preserva candidato e proveniência nas duas fixtures.

Uma tentativa `carddemo-v5-profile` usou posição inválida do argumento JFR; o
runner antigo classificou exit1 sem JSON como completo. É evidência inválida,
excluída das conclusões. O runner foi corrigido para exigir JSON. A repetição
`carddemo-v5-profile-corrected` confirmou OOM e gerou o JFR utilizado no diagnóstico.
O primeiro CardDemo v5 foi interrompido após falhas; não conta como corpus completo.

## Decisão e limites

O experimento justifica desenvolver uma análise de possibilidades por origens,
com compartilhamento de ciclos e saídas. Ele remove o multiplicador de contextos
e de pares redundantes na família que motivou a investigação. Não é só compressão
de objetos. O custo pode voltar a crescer com várias variáveis realmente relevantes,
reconstruções de textos e produtos de candidatos: se houver muitos nomes distintos
como resultado real, sua enumeração é inevitável.

Antes de levar ao produto: definir a política conservadora de alcance/retorno,
validar handlers e demais operadores, formalizar suporte por saída e ampliar os
contraexemplos de alias/grupos. Substituir o solver canônico só depois dessa
qualificação, sem manter fallback ou caminhos especiais por fixture.

## Evidência

Evidência bruta local: `benchmark/results/value-origin-20261009/`, incluindo comandos, inputs,
outputs, métricas, RSS/CPU/guardas, overlays de cada versão e JFR. Inventário SHA256:
`raw-sha256.json`, 4356 arquivos, hash `b8a92ca107115754734b9ab48b399c8acc0ecc22382fafb31b0d65940c07b02d`.
Resumo versionado: [summary.json](summary.json). Gerador: [report.py](report.py).
