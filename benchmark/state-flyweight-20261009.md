# Flyweight dos estados imutáveis — 2026-10-09

Foi encontrado compartilhamento adicional seguro, implementado no caminho
canônico e medido. A economia de payload é real, mas pequena nesta família:
o flyweight não resolve o crescimento das obrigações distintas.
[Comandos, hashes, histograma e contadores](state-flyweight-20261009.json).

## Discovery com objetos vivos

A fixture usa dois hubs de despacho externo, caixinhas com PERFORM cruzado,
GO TO de retorno e EXIT PARAGRAPH condicional. O censo ocorre depois de convergir
o fluxo de valores e antes de resolver as consultas. Um overlay isolado conta
identidades e igualdade exata nos estados retidos por posições, resumos,
chamadores e caches. Em seguida, o DiagnosticCommand da JVM coleta um histograma
**live**, com GC. Esse protocolo serve à memória retida; seus tempos não são
comparados com os tempos de produção. Não se produziu heap dump.

Em N=128, o censo encontrou 468.523 referências, mas apenas 4.893 objetos State.
Esses objetos possuíam **cinco conteúdos distintos**. Portanto, a aplicação já
reutilizava referências entre posições, mas construía payloads iguais em resumos,
projeções e patches independentes. Os 1.546 nomes de posição tinham 1.546
identidades e conteúdos distintos: não havia cópias de String para eliminar
nesse conjunto.

| Objetos vivos, N=128 | Antes | Flyweight |
|---|---:|---:|
| State | 4.893 / 195.720 B | 5 / 200 B |
| DependencyEnvironment, wrappers | 9.527 / 228.648 B | 10 / 240 B |
| Nós persistentes do ambiente | 2.068 / 82.720 B | 2.066 / 82.640 B |
| Location | 397.324 | 397.324 |
| HashMap.TreeNode | 387.166 | 387.166 |
| Cinco arrays de referências BitSet | 34.355.880 B | 34.355.880 B |

State e wrappers economizam **423.928 B, cerca de 414 KiB**. O heap vivo total
observado passa de 133.823.384 para 133.342.648 B, aproximadamente **0,36%**;
este total também inclui frontend, runtime e pequenas variações do histograma.
Os nós persistentes já eram compartilhados: eliminar wrappers não elimina
milhares de árvores copiadas, porque essas árvores não existiam.

Os maiores consumidores permanecem: arrays de metadados de células lógicas e
entradas do mapa BEFORE, com chaves Location distintas por contexto/posição.
Fatos BitSet iguais e topologia física já usam compartilhamento. Flyweight de
Location inteiro não fundiria obrigações diferentes sem alterar a semântica.
O número de TreeNode é uma observação de layout, não um diagnóstico de causa de
colisões feito nesta investigação.

## Implementação geral

`DependencyFlyweight<T>` conserva valores imutáveis por igualdade exata, com
WeakHashMap na chave e WeakReference no valor. Ambos os lados são fracos: o pool
sozinho não mantém payloads obsoletos vivos. Perder uma entrada apenas perde
compartilhamento; não muda o resultado. Cada análise possui seu próprio pool.

O fluxo retém payloads canônicos nos estados BEFORE, resultados de resumo,
entradas de chamadores, chaves de resumo/cálculo e patches. A igualdade inclui
os seis componentes de State: valores e UNKNOWN, handlers, fatos alcançados,
parâmetros, guarda de alcance e termos de controle. Não se unem conteúdos
aproximados, contextos ou vínculos de retorno. Os fatos de relevância usam a mesma
implementação de pool, substituindo sua rotina de interning anterior.

Não há flag, modo especial para hubs, solver alternativo ou cache global.
Payloads distintos ainda custam suas estruturas e a entrada fraca do pool:
a economia depende da repetição; não se promete ganho em fontes sem repetição.

## Medições de produção

Mesmos fontes byte a byte, JDK 21, JFR profile limitado a 24 MiB, uma JVM por vez,
`--max-work=1000000000`, reserva de 2.048 MiB para o host. O censo não participa
destas execuções. Heap de 1.024 MiB/RSS máximo 1.536 MiB em N=254; heap máximo de
4.096 MiB/RSS máximo 2.304 MiB em N=384. Timeout 150/180 segundos.

| Escala | Tempo antes | Tempo flyweight | RSS antes | RSS flyweight |
|---|---:|---:|---:|---:|
| N=254 | 24,04 s | 25,36 s | 970,6 MiB | 968,2 MiB |
| N=384 | 59,28 s | 57,47 s | 2.026,4 MiB | 1.956,4 MiB |

Ambas as versões convergem e produzem JSONs integralmente iguais nas duas
escalas. Todos os contadores de trabalho, contextos e relações permanecem iguais:
em N=384 são 8.278.286 células lógicas, 1.539 contextos, 4.733.199 itens de
trabalho e 1.181.955 entregas de valores.

Há somente uma execução por versão/escala. A diferença de RSS em N=384 é maior
que a economia direta de payload e pode envolver comportamento de alocação/GC;
não deve ser apresentada como redução reproduzível de 3,5%. Também não há ganho
consistente de velocidade: N=254 ficou mais lento e N=384 mais rápido.

N=512 não foi reexecutado: as tentativas anteriores foram interrompidas pelas
proteções de RSS/memória disponível. A economia pequena de payload não fornece
base para prometer que essa escala passou, nem justifica remover as proteções.
Não se observou OOM nas medições desta mudança.

## Validação

O kernel compartilhado invalida a evidência anterior de representação: a
validação focada verifica igualdade completa, UNKNOWN, parâmetros, handlers,
guardas, separação entre chamadores, colisões de hash e efeito de eviction.
As suítes adjacentes cobrem resumos correlacionados, escapes e preservação.
FAST e corpus são obrigatórios pelo AGENTS.md deste repositório.

Os 77 testes focados passaram. CardDemo passou nos 73 fontes: 815 dependências
iguais, zero perdas/acréscimos e os mesmos 47 PARTIAL, com os 185 hashes de
fontes/includes preservados. FIXTURE02, escape-sentinel (AFTER e ESCAPED) e o hub
com 255 destinos produzem os mesmos JSONs. O JAR isolado também passou.
FAST final passou em **1.093 testes / 142 suítes**, sem erros, falhas ou skips,
além dos testes Python do harness. A nova suíte DependencyFlyweightTest foi
incluída explicitamente no gate para CI. As 2.508 classes qualificadas são
byte a byte iguais às do JAR congelado usado nas medições. Os logs, histogramas, comandos completos, telemetria, GC, JFR e
JARs congelados permanecem em `benchmark/results/state-flyweight-20261009/`,
ignorado pelo Git. A coleta que falhou na compilação do overlay foi preservada;
o censo válido é `full-baseline-census-r2` e não altera as equações do solver.
