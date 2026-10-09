# Escala ampliada com heap de 4 GiB

Data: 2026-10-09. Produção inalterada, checkpoint `b82ec51`.
JAR congelado: `56896d52a204fe933a50e18fffc8fa3b383b0f0f10cc1ef4b67badcf0deefb41`.
[Comandos, contadores e hashes brutos](factored-large-heap-scale-20261009.json).

**Convergência confirmada até N=384, com teto de heap 4 GiB. N=512 permanece
inconclusivo porque as proteções externas interromperam os ensaios.** Não foi
registrado OOM do analisador; também não se presume que ele concluiria se as
proteções fossem removidas.

## Entrada e método

Mantido o padrão dinâmico `return-fanout` com dois hubs, seletores e flag vindos
de ACCEPT, PERFORMs cruzados e EXIT PARAGRAPH condicional. N indica caixinhas por
hub; o total é 2N. O gerador tinha teto artificial de N=254. Esse teto foi
removido; a largura do seletor cresce quando necessária para representar todos
os destinos e FINAL. O runner aceita qualquer heap positivo em MiB, mantendo
limites de RSS e disponibilidade do host independentes.

Nenhum algoritmo de produção, domínio de valores, retorno ou escape foi alterado.
Não usamos ablação. O JAR é exatamente o qualificado na etapa anterior. Os
99 casos antigos de geração (11 modos, três tamanhos e três números de hubs)
continuam idênticos byte a byte. O caso N=1024 foi preparado com seletor PIC 9(4),
2.048 caixinhas e 18.972 linhas; sua análise **não foi executada**.

JDK 21, uma JVM de análise por vez, JFR profile e GC log, timeout externo de
300 s e max-work 1 bilhão para não usar o antigo orçamento de 100 milhões como
obstáculo artificial de escala. Isso não corta candidatos. As proteções externas
não são limites do solver e seus resultados ficam separados de OOM/RESOURCE_LIMIT.

No início, a máquina tinha 14.837 MiB totais e 4.538 MiB disponíveis; a swap de
4.095 MiB estava cheia. Preservamos reserva de 2.048 MiB para o host. `-Xmx4g`
é um teto de heap, não a concessão de 4 GiB de RAM física livre. Tentar consumir
essa capacidade inteira nesta máquina colocaria a sessão em risco. Heap de
8 GiB não foi executado por esse motivo.

## Resultados novos

| N/hub | Caixinhas | Teto de heap | Resultado | Tempo observado | Pico RSS |
| ---: | ---: | ---: | --- | ---: | ---: |
| 320 | 640 | 4 GiB | PASS, 320 nomes exatos | 37,98 s | 1.565,5 MiB |
| 384 | 768 | 4 GiB | PASS, 384 nomes exatos | 56,76 s | 1.989,6 MiB |
| 512 | 1.024 | 4 GiB | Guarda de RSS; inconclusivo | 50,25 s até interrupção | 2.307,3 MiB |
| 512 | 1.024 | 2 GiB | Guarda de memória do host; inconclusivo | 51,77 s até interrupção | 2.237,1 MiB |

Na tentativa de 4 GiB/N=512, RSS ultrapassou o teto externo de 2.304 MiB. O
runner interrompeu o processo com SIGKILL, código -9. Na repetição com heap
2 GiB, o teto RSS era 2.560 MiB, mas a disponibilidade do host cruzou a reserva
de 2.048 MiB. Esses tempos são tempos **até a interrupção**, não tempos de análise
completa. Não há métricas finais nem resultado de dependências para N=512.
Uma repetição foi justificada para testar se coletas mais frequentes caberiam
na memória disponível; ela tampouco permite conclusão sobre convergência.

| N/hub | Posições físicas | Arestas físicas | Células lógicas | Fatos de saída | Contextos de valores |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 320 | 8.974 | 11.535 | 5.751.694 | 11.488.657 | 1.283 |
| 384 | 10.766 | 13.839 | 8.278.286 | 16.538.897 | 1.539 |

As contagens mantêm as fórmulas medidas anteriormente: posições `28N+14`,
arestas `36N+15`, células `56N²+54N+14`. N=384 tem 2,28 vezes as células de
N=254 e levou 2,32 vezes seu tempo anterior de 24,50 s. É compatível com
crescimento quadrático nesta família; ensaios únicos e condições diferentes
de heap/host não estabelecem uma lei universal de tempo.

N=320 teve 794,40 ms de pausas de GC; N=384, 1.085,25 ms. Em N=384, o JFR tem
3.616 amostras da main: 1.643 em controle, 1.084 em relevância e 370 em resolução
de consultas. O ensaio concluído não reproduz o ciclo prolongado de GC da execução
corporativa. O restante do custo continua ligado a relações lógicas e valores,
mesmo com topologia física compartilhada. Amostras de alocação não medem RSS nem
objetos retidos. JFRs de processos interrompidos podem estar incompletos.

## Reprodução em máquina com mais RAM livre

O runner interrompe quando a reserva do host é consumida, independentemente do
heap solicitado. Em uma máquina que possa fornecer a memória abaixo e conservar
a reserva, a próxima matriz é:

```sh
python3 benchmark/run-hub-dispatch.py OUTPUT \
  --jar target/cobol-dependency-analyzer.jar \
  --mode return-fanout --hubs 2 --boxes 512 768 1024 \
  --heap 8192 --rss 10240 --system-reserve 2048 \
  --max-work 1000000000 --timeout 1800 \
  --profile --profile-duration 1800 --telemetry
```

Usar OUTPUT novo, o JAR qualificado e uma análise por vez. Para repetir 4 GiB,
alterar para `--heap 4096 --rss 6144`, mantendo a reserva. Não executar essa
matriz nesta máquina esperando acesso integral aos 4 ou 8 GiB: sua RAM livre
foi o obstáculo observado. Guarda externa acionada continua sendo inconclusivo.

Logs, fontes, outputs, telemetria e perfis ficam em
`benchmark/results/factored-large-heap-20261009/`, ignorado pelo Git; seus hashes
estão no JSON versionado. O FAST de 1.088 testes e o CardDemo de 73 fontes/815
relações são evidência **reutilizada**, pois o JAR de produção e seus bytecodes
não mudaram; não foram reexecutados por uma alteração somente de benchmark.
A validade do runner foi verificada por argumentos inválidos, compatibilidade
byte a byte das entradas anteriores e os quatro ensaios do CLI real.

Não extrapolamos para o programa corporativo privado, nem afirmamos que N=512
ou N=1024 termina ou falha por OOM. O próximo ensaio precisa de mais RAM livre;
os resultados atuais mostram convergência medida até N=384 e preservam a
fronteira entre limite do produto e proteção da sessão.
