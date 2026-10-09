# Compartilhamento de decisões de controle

O solver canônico agora compartilha predicados e fluxo entre entradas. No caso
com doze flags, os contextos caíram de 8.192 para 14, as visitas de 135.164 para
137 e o tempo mediano do dataflow de 724,73 para 142,93 ms (**80,3% menor**).
Os mesmos dois destinos e suas localizações foram preservados. O heap permaneceu
em 512 MiB. A qualificação final passou; 47 limites históricos do CardDemo
continuam identificados como PARTIAL.

## Como o mecanismo evita a enumeração

Antes, PERFORMs com FLAG='Y' e FLAG='N' especializavam o corpo separadamente.
Com N flags independentes, o corpo podia ser visitado para cada combinação.
Agora o corpo começa com parâmetros, inclusive nos operandos das condições.
Um IF produz `TARGET = escolha(FLAG='Y', 'SPECIAL', ORIGIN)` e um guarda de
alcance. O resumo é vinculado à entrada inteira do chamador. Consultas usam BEFORE.

O DAG de decisões é ordenado e internado: ramos iguais desaparecem e sufixos
iguais compartilham nós. A álgebra divide todos os operandos pela mesma decisão
(cofatoração), preservando correlações: os pares (AAAA0000,1111) e
(BBBB0000,2222) produzem somente AAAA1111 e BBBB2222 numa escrita parcial.
Substituição e aplicação usam pilhas explícitas, sem recursão na pilha Java.

Guardas acompanham consultas literais e dinâmicas, retornos, escapes, handlers
ativos/salvos e fatos de controle. Um retorno inviável não ativa sua continuação.
Vínculos do mesmo contexto proprietário são unidos antes de expandir a camada
seguinte de resolução. A worklist usa a ordem reversa de pós-visita do controle
para reunir ramos antes de propagar junções pelo restante do fluxo.

Recorrências exigem um domínio finito para convergir. Escritas e refinamentos
repetidos, controles de repetição, suas fontes, aliases, grupos e tabelas entram
no fechamento de especialização. Os operandos de uma condição são fechados
juntos: um valor de recorrência não pode reentrar no DAG pelo parâmetro de outro
operando. Junções e escritas conservam esse domínio; predicados concretos abertos
admitem os dois ramos sem criar uma identidade booleana por iteração.

Essa distinção decorre da convergência, dentro do mesmo solver. Não há seleção
por tamanho, nome de programa, fixture ou estratégia antiga. O domínio simbólico
e o finito usam as mesmas operações concretas de leitura, atribuição e filtro.
Os experimentos descartados foram removidos da aplicação.

## O que o profiling acrescentou ao fix

A redução de contextos sozinha não bastou. Versões intermediárias falharam no
corpus por orçamento e RSS. Os logs e fontes dessas tentativas estão preservados
na evidência bruta, sem serem apresentados como qualificação do candidato final.

No COTRN02C, uma inspeção do grafo encontrou 63.560 nós retidos contra 4.537 nós
alcançáveis das estruturas operacionais. A memoização da álgebra e da aplicação
passou a durar uma operação; o índice canônico mantém referências fracas, enquanto
cada nó vivo mantém sua chave. Assim uma operação compartilha seu trabalho, mas
seu histórico inteiro não permanece residente. Saídas PROGRAM_RETURN/HALT
retêm alcance, pois seus valores não têm continuação consumidora; observações
anteriores continuam no estado BEFORE.

No COBIL00C, o JFR limitado apontou substituição, aplicação e recomposição de
decisões. Instrumentação por linha encontrou 1.217 refinamentos na comparação
da linha 388 e 569.116 nós criados antes do limite reduzido de 10.000 células.
A comparação tinha um operando que o domínio atual representa como TOP,
independente dos parâmetros. Para qualquer candidato do outro operando, ambos
os resultados são possíveis: o filtro é uma identidade. Criar um refinamento
novo em cada ramo distinguia valores que não haviam mudado.

A regra geral agora conserva o valor nesse filtro, mantendo o guarda do ramo.
Não existe reconhecimento especial de DFHRESP no solver. O novo teste com oito
EVALUATEs opacos falhou antes do ajuste: 13.120 predicados e 1.349.643 nós. O
candidato preserva FIRST, SECOND e KEEP com **32 predicados e 120 nós**. O COBIL00C
que foi interrompido a 664.240 KiB concluiu em 3,41 s, com 323.828 KiB de RSS e
JSON/diagnósticos idênticos. São medições isoladas, não medianas de um A/B.

A integração também exigiu corrigir o consumo de construções do frontend:
qualificadores identificam escopo, não leem seu grupo; WHENs alternativos do
mesmo corpo usam OR e posições ALSO usam AND, incluindo EVALUATE FALSE.
Escritas imprecisas abrem os valores nominais afetados e suas equivalências,
sem capturar o vetor inteiro de aliases como operandos de cada campo.
Uma escrita que permanece fechada mesmo com TOP nos operandos simbólicos vira
constante, sem carregar a entrada sobrescrita. São regras do domínio nominal;
nenhum parser, solver paralelo ou representação de memória física foi adicionado.

O teste negativo com IF FLAG='Y', ACCEPT FLAG e IF FLAG NOT='Y' preserva CHANGED:
uma mutação desconhecida não pode herdar a identidade do valor anterior.
Há regressões para correlação de escritas parciais, ramos contraditórios,
retornos sem continuação, handlers condicionais, grupos de extensão desconhecida,
WHEN/ALSO/FALSE, qualificadores e definições constantes.

## Medição do candidato final

Baseline: produção `88dbd63c7adab0705e8443ad16d98f6303c23614`, JAR
`afae3a62c961dd5ae03a0cc29cbbfe9fca6d1aa54e0952d017431e0608ad200f`.
Candidato: JAR `fae9d87745f415c54dd5815b0bced41fabcac6e498944f01c7e110731eadedfb`.
Base da branch: `9782918026909d9a3c8a3204b1a3569111004251`.
Os hashes de produção/testes e das saídas estão no [registro estruturado](guarded-flow-20261009.json).

Temurin 21.0.12+1.1; JVMs novas e sequenciais, heap 512 MiB, metaspace 256 MiB,
memória direta 64 MiB. Supervisor: RSS agregado 640 MiB; tempo de 30 s nas flags
e 60 s no corpus/adicionais. Orçamento 1.000.000; FIXTURE02 usa 20.000 em ambas
as versões. JFR de até 16 MiB nos adicionais; leitores em streaming, heap 128 MiB.
Nenhum orçamento ou heap foi aumentado para obter PASS.

Foram 48 execuções das flags: três repetições A/B intercaladas para N=4,8,12;
uma para N=6,10 e somente candidato em N=16,20. Os fontes de N=10 e seus
hashes/oráculos versionados ficaram inalterados. Cada execução confere programa,
nomes, tipos, cardinalidade, localização e ausência de diagnósticos. A/B exige
JSON byte a byte idêntico. Os tempos abaixo são medianas; N=16,20 têm uma execução.

| Flags usadas em IF | Contextos antes → depois | Visitas antes → depois | Dataflow antes → depois |
|---:|---:|---:|---:|
| 4 | 32 → 6 | 268 → 49 | 48,21 → 81,03 ms |
| 8 | 512 → 10 | 6.396 → 93 | 116,19 → 111,87 ms |
| 12 | 8.192 → 14 | 135.164 → 137 | 724,73 → 142,93 ms |
| 16 | não executado → 18 | não executado → 181 | não executado → 164,27 ms |
| 20 | não executado → 22 | não executado → 225 | não executado → 190,62 ms |

N=20 admite 1.048.576 combinações por construção (2^N); o candidato não as
enumera. Usa 22 contextos, 1.806 nós criados, 16.200 células de álgebra e 1.760
itens de resolução, com pico RSS de 160,7 MiB. Nesta família, contextos e visitas
crescem linearmente; nós e resolução ainda crescem quadraticamente.

Há custo nos casos sem explosão. O tempo interno total de N=4 com predicados
passou de 530,58 para 568,07 ms. Em N=12 com chamadas diretas aos dados, os
contextos continuam em 14, o dataflow passou de 74,02 para 115,99 ms e o total
interno de 598,95 para 653,34 ms. Não há ganho universal.

## Qualificação e casos adicionais

- Focados: 94 testes, zero falhas/erros/skips.
- FAST final: 1.069 testes, zero falhas/erros/skips; gate de nomes preservado.
- Discovery: 40/40 fontes e seus hashes conferidos; JSONs, diagnósticos e códigos
  de saída idênticos à referência congelada. Pico RSS 453,6 MiB.
- CardDemo: 73/73 fontes e includes conferidos; 815 pares de dependência, zero
  ausentes ou extras. Os 73 JSONs e diagnósticos também são idênticos byte a byte.
  Permanecem 26 PASS e 47 PARTIAL. Rodada única: 119,88 s, pico RSS 398,1 MiB.
- Adicionais: 12 execuções A/B intercaladas, três repetições de cada versão para
  FIXTURE02 original e 400 chamadas; saídas byte a byte idênticas.
- Standalone: JAR isolado em diretório/ambiente vazio, heap 256 MiB, saída dinâmica
  mínima exata; não depende de outros repositórios.

A FIXTURE02 mantém SHA `71408d8c98ae04600f119ad98fd3e16c3066c20babdec8c4ab6ada8632a9c9d3`.
Ambas as versões usam 25 contextos e 200 visitas e retornam zero dependências:
os PERFORMs incondicionais atingem um ciclo sem retorno antes dos CALLs.
Medianas do candidato: dataflow 131,16 ms, total interno 1.387,63 ms; baseline
86,08 e 1.337,10 ms. Pico RSS do candidato 286,9 MiB. O fonte continua sendo uma
reconstrução: somente seis parágrafos foram conferidos contra o vídeo.

As 400 chamadas preservam todos os 400 destinos. Ambas as versões já usam dois
contextos e 1.668 visitas; este caso não provoca a explosão de predicados.
Dataflow mediano 201,45 → 249,59 ms; total interno 988,09 → 1.034,84 ms. Pico RSS
213,3 → 195,8 MiB. São custos medidos, não evidência de aceleração nesse caso.

`decisionNodes` é cumulativo, inclusive nós coletados; `predicateInputs` conta
predicados internados. `decisionOperations` e `liftedOperations` somam células
visitadas por aplicação. Não são tamanhos residentes dos caches. O orçamento
limita cada aplicação, além dos limites existentes de estados, predicados,
resumos e candidatos; operações terminadas não consomem memória indefinidamente.

Logs, comandos, fontes, JSONs, métricas, JFRs e o manifest de hashes permanecem em
`benchmark/results/guarded-flow-20261009/qualified-v2`, ignorado pelo Git.
As tentativas intermediárias permanecem ao lado, com seus resultados originais.
Este relatório usa somente o candidato final nos gates de qualificação.
Endpoints, handlers e entradas de recorrência ainda podem distinguir resumos.
Um DAG pode crescer com a função e a ordem de decisões; produtos de candidatos
e resolução têm custos próprios. O fix não garante qualquer programa real de
117 mil linhas nem elimina toda explosão combinatória. Falhas continuam explícitas,
sem truncar candidatos ou publicar parcialmente uma saída anterior.
