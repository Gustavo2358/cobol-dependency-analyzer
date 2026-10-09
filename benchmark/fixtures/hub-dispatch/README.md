# Dispatch externo com caixinhas

Fontes FIXED produzidas pelo gerador de hubs. Há dois hubs; cada ACCEPT representa
a próxima escolha externa, e FINAL-BOX termina. Cada caixinha volta por GO TO.
Os modos hub-perform também chamam o próximo hub antes dessa transferência.

`expected.json` fixa hashes e os candidatos exatos caso a análise conclua.
flags-16 é um adversário de recursos: não afirmar PASS apenas porque falhou.
frozen-flags-16 altera os valores iniciais para isolar o crescimento de entradas;
seu oráculo intencionalmente exclui ZERO0000.

Os controles de 255 destinos usam 254 caixinhas e FINAL-BOX por hub. flags-8
mostra 255 subconjuntos não vazios de oito flags na entrada dos hubs, apesar de
ter poucos destinos. cross-perform-32 explora fronteiras distintas de invocação.

Resultados e limites estão no [relatório](../../hub-dispatch-20261009.md).
