# Auditoria dos 11 adicionais do CardDemo

Baseline `52c1b82dc6accbb615818cf5b5298843a85b0f01`; experimento `332abaf`.
Auditoria por leitura dos fontes, grafo físico e definições instrumentados,
e ablações temporárias. Nenhuma alteração no solver do experimento ou na produção.
Dados estruturados: [carddemo-additions-audit.json](carddemo-additions-audit.json).

Os 11 registros correspondem a seis fontes distintos. COBIL00C, COCRDSLC,
COUSR02C, COUSR03C e COTRN01C também aparecem como `.cl2`, com SHA-256 idêntico
e o mesmo conjunto de diretórios de COPY, em ordem diferente. As cinco cópias
foram reexecutadas com sua ordem original de diretórios. O inventário de 185
hashes de fontes/COPY foi novamente conferido. Os paths e hashes de cada
registro estão no JSON.

| Fonte | Adicional | Registros | Uso | Resultado da auditoria |
|---|---|---:|---:|---|
| COPAUS0C | programa COPAUS0C | 1 | 674–677 | Valor de inicialização alcança XCTL por retornos/travessias artificiais |
| COBIL00C | programa COBIL00C | 2 | 281–284 | FROM escrito em 279 volta à cópia FROM→TO de 132 |
| COUSR02C | programa COUSR02C | 2 | 258–261 | FROM escrito em 256 volta à cópia FROM→TO de 116 |
| COUSR03C | programa COUSR03C | 2 | 205–208 | FROM escrito em 203 volta à cópia FROM→TO de 115 |
| COTRN01C | programa COTRN01C | 2 | 205–208 | FROM escrito em 203 volta à cópia FROM→TO de 119 |
| COCRDSLC | arquivo CARDAIX | 2 | 783–791 | READ em parágrafo não chamado, além do endpoint de PERFORM THRU |

## O nome do próprio programa volta no tempo

COBIL00C declara `WS-PGMNAME VALUE 'COBIL00C'`. No tratamento de PF3, as
linhas 129–135 escolhem o destino a partir de CDEMO-FROM-PROGRAM e executam
RETURN-TO-PREV-SCREEN. Dentro desse parágrafo, a linha 279 escreve o próprio
nome em FROM; o XCTL de 281–284 transfere para o destino previamente escolhido.
FROM e TO são campos distintos, sem REDEFINES, em COCOM01Y.cpy, linhas 22 e 24.

```text
Fluxo do fonte:
  FROM recebido -> TO recebe FROM -> FROM recebe COBIL00C -> XCTL TO

Ciclo observado no grafo aproximado:
  FROM recebe COBIL00C [279]
       -> XCTL [281]
       -> continuação de condição admitida sem verificar disposição
       -> fim de RETURN-TO-PREV-SCREEN
       -> fallthrough para SEND-BILLPAY-SCREEN
       -> fallthrough para RECEIVE-BILLPAY-SCREEN
       -> retorno compartilhado para o EVALUATE anterior [125]
       -> cópia FROM para TO [132]
       -> XCTL TO [281]
```

Assim o nome escrito **depois** da escolha do destino contamina a escolha
**anterior**, graças a um ciclo inventado. COUSR02C, COUSR03C e COTRN01C têm a
mesma cadeia; suas linhas específicas estão na tabela e no JSON.

Não há HANDLE/IGNORE CONDITION, NOHANDLE ou RESP nesses XCTLs e tampouco um
registro de condição nos quatro fontes expandidos. A topologia contém a
possibilidade do evento PGMIDERR; `DependencyFlow.controlEffect` inclui sua
continuação no grafo conservador. O solver principal de valores só a executa
para disposição IGNORE. O experimento consome essa possibilidade sem testar
a disposição. Não foi uma aresta normal de retorno do XCTL.

O XCTL bem-sucedido transfere controle e libera o programa de origem; PGMIDERR
tem como ação padrão terminar a tarefa. A referência é a
[documentação IBM de XCTL](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-xctl).
Tratamentos explícitos podem permitir outros fluxos; não devem ser eliminados
por uma regra geral de que todo XCTL sempre termina.

**Limite da classificação:** os quatro programas recebem FROM via DFHCOMMAREA.
Um chamador externo poderia fornecer o próprio nome. Portanto, não se provou
que a autorreferência seja impossível em qualquer ambiente. Provou-se que o
testemunho local que introduziu esse candidato concreto no experimento é
inválido. O input externo permanece um restante dinâmico, sem autorizar deduzir
esse nome específico a partir de uma atribuição posterior ao uso.

## COPAUS0C junta a inicialização com outra continuação

No ramo EIBCALEN=0, a linha 192 coloca WS-PGM-AUTH-SMRY (`COPAUS0C`) em TO;
a linha 198 chama SEND-PAULST-SCREEN. O retorno correto dessa chamada continua
no MAIN, que termina com CICS RETURN nas linhas 254–257. Guardar TO para outra
interação não é executar um XCTL para esse destino nesta execução.

A chamada explícita a RETURN-TO-PREV-SCREEN em 237 é precedida pelo MOVE de
`COMEN01C` em 236. O outro XCTL, em 322–326, recebe `COPAUS1C`, atribuído em 316.
Não há GO TO/ENTRY alternativo que leve a inicialização diretamente ao XCTL 674.

A instrumentação identifica a definição de TO da linha 192 como origem do
adicional. Um caminho do grafo que conserva essa definição retorna da chamada
SEND de 198 para a continuação de **outra chamada SEND**, a de 995. Ele passa
por GET-AUTH-SUMMARY e usa o retorno de outra chamada em 370; depois atravessa
o fim de INITIALIZE-AUTH-DATA, caindo em RETURN-TO-PREV-SCREEN. Esse caminho
evita os MOVEs de 236, 316 e 669 que substituiriam o candidato.

Portanto, a atribuição existe e o XCTL existe, mas a ligação entre ambos vem
do pareamento perdido e da continuação indevida em limites de PERFORM. Não é
uma dependência local demonstrada pelo fonte.

## CARDAIX está depois do ponto onde o PERFORM deve voltar

COCRDSLC define LIT-CARDFILENAME-ACCT-PATH como `CARDAIX ` nas linhas 189–190.
O READ de 783 usa essa variável, mas está em 9150-GETCARD-BYACCT. Não há chamada
PERFORM, GO TO ou ENTRY para esse parágrafo no fonte expandido.

As chamadas de leitura em 344 e 365 usam 9000-READ-DATA THRU seu EXIT. Dentro
de 9000, as linhas 728–729 executam:

```text
PERFORM 9100-GETCARD-BYACCTCARD THRU 9100-GETCARD-BYACCTCARD-EXIT
  -> READ CARDDAT [742]
  -> 9100-GETCARD-BYACCTCARD-EXIT [775–777]
       -> retorno correto para 9000
       -> continuação adicional do experimento para 9150
            -> READ CARDAIX [783]
```

A instrumentação confirma a aresta COMPLETE do EXIT para a entrada de 9150.
Para essa invocação, o endpoint deve retornar ao chamador. O experimento
conserva simultaneamente o retorno e o ordinary fallthrough. O arquivo é
realmente mencionado no fonte, mas seu READ é inalcançável pelo fluxo legítimo
analisado. São dois falsos positivos comprovados, um por cópia do fonte.

## Confirmação por intervenções temporárias

Onze runs instrumentadas reproduziram integralmente os JSONs anteriores,
incluindo as cinco cópias `.cl2`. Depois foram feitas 23 runs em builds separados:
os seis fontes distintos para cada corte e as cinco cópias para o corte combinado.

| Corte de investigação | Adicionais removidos nos seis fontes distintos | Dependências da baseline perdidas | Localizações da baseline alteradas |
|---|---:|---:|---:|
| Omitir continuação de condição quando não há registro de condição no programa | 4 autorreferências | 0 | 0 |
| Omitir ordinary fallthrough em COMPLETE que também é endpoint de alguma chamada | 6 adicionais | 0 | 0 |
| Ambos, incluindo as cinco cópias | 11 adicionais em 11 registros | 0 | 0 |

Os seis fontes distintos contêm 84 dependências da baseline; todas foram preservadas
nessas intervenções. Nos 11 registros, incluindo as cópias, são 148 dependências
da baseline, todas preservadas no corte combinado. Os seis casos explicam
os 11 adicionais. Não se reexecutaram os outros 62 casos, nem se atribui uma
nova qualificação de 815 dependências às ablações.

Esses cortes **não são correções gerais**: por exemplo, um parágrafo pode ser
endpoint de uma chamada e intermediário de outra, que precisa continuar.
Os cortes isolam a causa; promovê-los sem distinguir essas situações poderia
perder dependências. O diagnóstico indica recuperar apenas as distinções de
controle necessárias, não reiniciar a enumeração de ambientes inteiros.

Todas as JVMs foram sequenciais, heap de 128 MiB, RSS limitado a 768 MiB,
reserva de 2 GiB no host e timeout de 60 s. Pico observado de RSS: 165.5 MiB.
Nenhuma run teve falha de recurso. Os resultados continuam PARTIAL porque a
aproximação e os inputs externos permanecem. Não foi executado FAST: o delta
é de documentação/evidência e os cortes existem somente em builds de pesquisa.

Evidência bruta: `benchmark/results/carddemo-rd-audit-20261009-final/`.
Ali estão graph.json, outputs, comandos, telemetria, fontes instrumentados e
scripts. A primeira tentativa de instrumentação teve erro de compilação e
permanece separada em `carddemo-rd-audit-20261009/`; não foi contada como run.
