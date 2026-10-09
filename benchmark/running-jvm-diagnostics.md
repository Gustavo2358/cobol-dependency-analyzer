# Coleta na execução corporativa que permanece ativa

Os fixtures reproduziram crescimento de contextos e GC caro, mas não reproduziram
70 minutos de execução com RSS estável sem OOM. O relato de `-Xmx16g`,
`--max-work 100M` e RSS próximo de 17 GB não identifica o algoritmo responsável.
RSS não mede apenas objetos vivos; o limite de trabalho não é um prazo de execução
e não contabiliza individualmente cada operação realizada dentro de uma visita.

O [coletor](collect-running-jvm.py) observa uma JVM Linux já em execução. Execute
na mesma máquina/container e com o mesmo usuário da análise, usando o JDK da JVM:

```sh
python3 benchmark/collect-running-jvm.py PID /tmp/cobol-jvm-probe --jdk /caminho/do/jdk
```

Substitua PID pelo identificador Java. O diretório de saída deve ser novo. O JFR
usa um caminho absoluto que precisa ser visível e gravável também pela JVM alvo.
Não é necessário reiniciar a análise nem alterar seus limites. A coleta não
solicita heap dump, GC completo ou encerramento do processo alvo.

Durante aproximadamente 60 segundos, são coletados:

- Quatro `Thread.print -l`, para comparar pilhas e tempo acumulado por thread.
- Ocupação via `GC.heap_info` e contadores cumulativos via `jstat -gc`, quando disponível.
- RSS e tempo de CPU do processo e de cada thread em `telemetry.jsonl`.
- JFR `profile` de 60 segundos, com retenção limitada a 32 MiB.
- Comandos, tempos e falhas em `manifest.json`; cada cliente de diagnóstico tem
  heap de 64 MiB e timeout de 15 segundos. O timeout encerra somente o cliente.

O JFR adiciona overhead e buffers à JVM alvo. A gravação termina automaticamente,
inclusive se o coletor for interrompido. A coleta pede breves operações de
diagnóstico à JVM, portanto não é uma medição de impacto zero. Falha no attach
inicial interrompe a coleta e preserva o erro; verifique usuário, namespace,
permissões e se o attach está desabilitado. Erros posteriores permanecem no
manifest: arquivos incompletos não constituem evidência de uma fase ausente.

## O que essa evidência distingue

Compare o aumento de CPU por thread entre amostras: o `tid` decimal da telemetria
corresponde ao `nid` hexadecimal nas pilhas Linux. Isso permite identificar a
thread ocupada, mesmo quando ela não se chama `main`.

Se tempo de GC e número de coleções crescem rapidamente enquanto o heap permanece
quase cheio, temos evidência de pressão de memória. Se a thread de análise acumula
CPU com pouco GC, as pilhas e amostras JFR localizam o trabalho Java: propagação
dos resumos de controle, relevância, junção de estados, organização da fila ou
resolução de consultas. `DependencyFlow.analyze` engloba essas fases e, sozinho,
não as distingue.

Repetir o mesmo frame indica uma região quente; não prova um ciclo infinito.
O JFR também não mede cardinalidade de contextos ou redução da fila. Depois de
localizar a região, a instrumentação de seus contadores poderá distinguir trabalho
finito muito grande de falta de progresso. Esse diagnóstico permanece pendente
até receber a coleta do programa real.

Para uma primeira inspeção textual no JDK:

```sh
/caminho/do/jdk/bin/jfr summary /tmp/cobol-jvm-probe/profile.jfr
/caminho/do/jdk/bin/jfr print --events jdk.ExecutionSample,jdk.GarbageCollection --stack-depth 32 /tmp/cobol-jvm-probe/profile.jfr > /tmp/cobol-jvm-probe/samples.txt
```

O leitor histórico `profiling/JfrPhaseStats.java` filtra a thread `main`; use-o
apenas quando a análise ocorre nessa thread. As amostras cruas preservadas são
necessárias quando a thread tem outro nome.

## Validação do coletor

Teste local em 09/10/2026 com Temurin 21, JVM descartável de heap 32 MiB e janela
de oito segundos: dois snapshots completos, todos os comandos com código zero,
JFR produzido e alvo ainda em execução ao final. O teste encerrou somente sua
própria JVM após verificar esse resultado. Evidência bruta em
`results/running-jvm-collector-smoke-host/` (ignorada pelo Git).

Dois testes anteriores no sandbox falharam no attach; os erros permanecem em
`results/running-jvm-collector-smoke/` e
`results/running-jvm-collector-smoke-listener/`. A validação funcional foi feita
fora dessa restrição, com alvo descartável, sem acessar o programa corporativo.
Também passaram os casos de PID inválido, falha de attach com parada imediata e
preservação de diretório existente. Não houve mudança semântica no analisador;
o JAR manteve SHA-256
`b7cc0654207091e1872071a7a3267547daa170ce9b1fb1026702c99e69fa5ab1`.
