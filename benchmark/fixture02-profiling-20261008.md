# FIXTURE02: profiling da explosão de memória — 8/10/2026

A fonte original falha com heap de 4 GiB tanto na versão anterior quanto no fix em preparação. O crescimento dominante está nos resumos indexados por estado de entrada e na reconstrução de seus ambientes. O índice compartilhado de relevância estabiliza antes desse crescimento. A otimização anterior não resolve este fixture.

## Insumo e status

[Fixture e procedência](../src/test/resources/dependency-regression/video-reconstructed/README.md): 2.000 campos, 150 parágrafos, 450 IFs, 150 PERFORMs e 150 CALLs, 4.114 linhas. A sequência reconstruída não foi conferida integralmente com o vídeo. A cópia FIXED muda somente colunas e comentários; o corpo livre e o corpo convertido foram comparados. SHA da cópia FIXED: `71408d8c98ae04600f119ad98fd3e16c3066c20babdec8c4ab6ada8632a9c9d3`.

JAR do fix: `b3f266e5a553a120488841d1428f2947434bb52487bb03661728b504885785d4`; JAR anterior: `c962031a19228b1e52c13c8262a06e92fb07aeed3aae3660db5f28efa0b2c0b8`. Ambos usaram Temurin 21.0.12+1.1, heap de 4 GiB e orçamento de 100 milhões.

| Execução da fonte original | Resultado | Tempo externo | RSS máximo |
|---|---|---:|---:|
| Fix em preparação, sem profiling | OOM, código 2; sem JSON | 97,98 s | 4.428.996 KiB |
| Versão anterior, sem profiling | OOM, código 2; sem JSON | 156,44 s | 4.426.236 KiB |
| Fix, JFR sem alteração de código | OOM, código 2; sem JSON | 102,76 s | 4.453.440 KiB |

A tentativa com 8 GiB foi interrompida e não tem resultado final; não é PASS nem OOM confirmado. Depois da orientação de não aumentar heap e proteger a sessão, os novos experimentos usaram somente 512 MiB e parada diagnóstica em 2.048 contextos. O pós-processamento binário dos dois JFRs usou heap de 128 MiB e picos de RSS de 113.232/127.160 KiB. A exportação JSON completa anterior gerou ~953 MiB e foi substituída por leitura de eventos em streaming.

O esperado operacional da fonte original é vazio, por inspeção independente do fluxo: cada CALL vem depois de um PERFORM incondicional; a cadeia de P00000 entra num ciclo sem base de retorno em P00063. Os IFs não pulam esses PERFORMs. Os GO TO impedem queda até FINAL-PARA. A análise falhou antes de comprovar/publicar esse resultado.

## Evidência do algoritmo responsável

Foram coletados JFR de execução/alocação/objetos antigos, logs de GC, cinco histogramas de objetos vivos e amostras de pilha. Uma cópia diagnóstica de três classes adiciona apenas contadores e stack trace de OOM; não modifica o código operacional nem o JAR qualificado. A coleta de attach na primeira execução sandboxed falhou; essas tentativas permanecem registradas. A segunda coleta conseguiu attach e histogramas.

A pilha exata de OOM é:

```text
DependencyEnvironment.insert
DependencyEnvironment.with
DependencyEnvironment.copyOf
DependencyFlow.State.<init>
DependencyFlow.subscribe
DependencyFlow.invoke
DependencyFlow.phase
```

No JFR do JAR sem instrumentação, a projeção dos resumos corresponde a 2.584/4.025 amostras de execução da thread main (64,20%) e a 87,21% dos bytes de alocação estimados. State.join corresponde a 29,34% das amostras e 9,14% das alocações. Do peso de alocação de DependencyEnvironment.Node, 99,51% tem subscribe na pilha. Esses números são amostras Java e estimativas de alocação cumulativa; não são percentuais de tempo externo nem bytes retidos.

O perfil com contadores confirma a distribuição: 61,38% das amostras e 86,71% das alocações na projeção. No histograma de 90 s, há 100.911.670 nós de DependencyEnvironment, com 4.036.466.800 bytes rasos: 94,57% dos 4.268.189.440 bytes vivos inventariados. São objetos vivos após GC, distintos das estimativas de alocação do JFR.

Os contadores próximos à falha mostram:

- 18.384 pontos de relevância, 768 posições de entrada e 181.853 operações de relevância estabilizados;
- 120.000 contextos para apenas 24 pares de entrada/endpoint;
- 759.314 estados BEFORE e 94.173 itens pendentes;
- 92.096.888 entradas de valores somadas nas chaves dos contextos; 90.659.128 (98,44%) são UNKNOWN puro.

Essa soma conta posições em mapas, não identidades únicas de nós. O histograma mede os objetos reais. A chave usa o estado projetado completo: diferentes subconjuntos de campos com X/UNKNOWN gerados pelos IFs entram como resumos distintos. Ao construir cada chave, subscribe cria um HashMap e State chama copyOf, que reinsere todas as entradas numa árvore persistente nova. A persistência ajuda as atualizações dentro do fluxo, mas o compartilhamento estrutural se perde nessa projeção. O custo retido das chaves cresce aproximadamente com contextos × largura da projeção; a multiplicação dos contextos vem antes do ciclo completo convergir.

## Experimentos causais pequenos

Todos são cópias diagnósticas, não substituições da fonte original nem novos oráculos de produto. Heap 512 MiB, uma JVM por vez, limite de instrumentação de 2.048 contextos. O código 2 do primeiro é a parada deliberada, não OOM nem qualificação aprovada.

| Variante | Contextos | Itens de trabalho | RSS máximo | Resultado |
|---|---:|---:|---:|---|
| Original, parada diagnóstica | 2.048 | 15.282 visitas antes da parada | 404.752 KiB | DIAGNOSTIC_STOP |
| Só MOVE 'X' condicional → CONTINUE | 25 | 200 | 263.356 KiB | código 0, dependências vazias |
| Só PERFORM → CONTINUE | 9 | 323 | 257.952 KiB | código 0, 10 destinos literais conferidos |

As duas primeiras conservam os mesmos 860 campos rastreados, 2.000 declarações, 450 IFs, 150 PERFORMs, 150 CALLs e 150 GO TO. O índice conserva os mesmos 18.384 pontos; seu conjunto de entradas muda de 768 para 749 posições. Ao retirar somente a alteração condicional dos valores, a quantidade de contextos desaba para 25. Isso separa a cardinalidade de estados de entrada do tamanho do programa e da topologia.

Na parada do original, há 2.048 contextos para 16 pares de entrada/endpoint. Dois pares têm 512 contextos cada. Amostras das chaves registram diferentes combinações de campos com `values=[X       ], unknown=true`, enquanto o restante permanece UNKNOWN. O histograma desse experimento contém 1.638.993 nós do ambiente (65.559.720 bytes). A soma das chaves é 1.571.784 posições, aproximadamente 767 por contexto.

Na variante sem PERFORM, a cadeia de GO TO que parte de P00000 determina o oráculo de dez nomes. A junção de branches no fluxo ordinário converge com nove contextos. Essa variante altera o comportamento do programa; serve somente para isolar o custo da invocação contextual.

## Consequência para a correção

Não há evidência de que ampliar mais o heap ou otimizar o fechamento de relevância resolva o mecanismo deste fixture. A melhoria anterior é real nos seus controles, mas não elimina a enumeração de estados de entrada nesta recursão.

Há duas frentes diferentes: preservar compartilhamento estrutural na projeção reduz o custo por contexto; evitar resumos por cada combinação exige rever o tratamento da entrada em regiões recursivas e/ou a demanda observável com retornos realizáveis. A segunda é uma mudança semântica e precisa preservar correlação de chamadores, retorno de PERFORM, escapes e handlers. Uma junção indiscriminada de entradas não está qualificada por estes dados. Nenhuma dessas hipóteses foi aplicada ao produto nesta investigação.

O PR é apresentado em rascunho, com este finding explícito. A fonte original não foi simplificada, os oráculos de paridade não foram alterados, e não há declaração de robustez universal baseada nos fixtures anteriores.

Resumo e hashes dos artefatos: [fixture02-profiling-20261008.json](fixture02-profiling-20261008.json). Perfis binários, histogramas, fontes das cópias diagnósticas, comandos, contadores e falhas de coleta ficam preservados localmente em `results/fixture02-profiling-20261008/`; builds diagnósticos não são versionados.

Arquivo bruto local: `results/fixture02-profiling-20261008.tar.gz`, SHA-256 `c7d6aec92d85b0f40a8b0426cb655d4cc734422b99d966721cc30e4c443727de` (108 arquivos). O manifest lista os hashes; derivados JSON grandes e classes compiladas foram omitidos, com os JFRs e o leitor em streaming preservados.
