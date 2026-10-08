# Compartilhamento de caudas de PERFORM — 8/10/2026

O custo aproximadamente quadrático caiu para crescimento próximo do linear
nas famílias estruturadas AND e overlap testadas, mantendo as saídas completas.
Overlap1800 passou de **42,09 s para 4,37 s**, de **3.078,9 MiB para 514,8 MiB**
de RSS e de **9.800.224 para 33.926** itens de trabalho. São 9,63 vezes de
ganho no tempo externo e redução de 83,28% no RSS desta amostra.

Produção anterior: `f70a94bdb4441fc8bf4fa2b6cb8ecec4f3059c33`, JAR
`7eddd887626b3dce0bca205a51fa88424ed07d7aae4ad8e3bd8e0061a6e4f10a`.
Produção otimizada: `981e1c535b47f7ffeba27cbaa16084dac825b249`, JAR
`64c403308574e82916c93e80e2ba58912c4de601668b5c3ba70bc61eb209d554`.
Os SHAs dos produtos diferem dos commits que apenas registram documentação.
Os resultados normalizados e hashes dos relatórios brutos ficam em
[suffix-optimization-20261008.json](suffix-optimization-20261008.json).

## Arquitetura e algoritmos

Antes, duas invocações com entradas diferentes e o mesmo retorno processavam
separadamente todos os parágrafos da cauda comum. Em `PERFORM P-i THRU P-n`
para cada `i`, o número de parágrafos visitados segue a soma dos comprimentos
dos intervalos. O código já era compartilhado; seus estados de execução ainda
se repetiam por contexto.

Agora a entrada de um parágrafo pode delegar a um resumo da cauda. Sua chave
contém **entrada do parágrafo, endpoint de retorno e estado lógico completo
recebido**. Entradas equivalentes reutilizam o mesmo resultado; os chamadores
assinam sua conclusão, mantendo suas continuações originais. Não há projeção
nova que elimine valores da chave. A worklist existente continua tratando
branches, loops, efeitos lógicos e PERFORMs aninhados. As conclusões das caudas
são enfileiradas, evitando desempilhar recursivamente uma cadeia longa.

As consultas continuam observando os estados BEFORE registrados nos trechos
compartilhados, incluindo consultas internas e sua proveniência. O resumo não
se limita a guardar o estado final e não elimina observações intermediárias.
Intervalos que retornam em endpoints diferentes têm chaves diferentes.

A preparação também mudou: o conjunto de leituras/escritas relevantes era
recalculado por BFS para cada intervalo. Nos programas elegíveis, um grafo de
predecessores por endpoint propaga esses conjuntos para trás por união até
o ponto fixo. Entradas que compartilham uma cauda reutilizam a mesma preparação.
A projeção conserva as relações de grupos, aliases e elementos de tabelas.
Esta etapa inclui corpos aninhados e continuações, como o cálculo anterior.

A elegibilidade é conservadora e vale para a unidade de programa inteira.
Transferências explícitas, escapes contextuais, source continuations com
pré-requisitos, registros/eventos de handlers, fluxo de arquivo e handlers de
CALL mantêm o solver anterior. Nesses casos, o resultado pode depender de
histórico ou de obrigações de controle que ainda não fazem parte da chave da
cauda. Não há teste de nome, tamanho ou formato específico de fixture.

## Comparação nova, com os mesmos recursos

Seis casos foram medidos novamente com ambos os artefatos. A configuração foi
Temurin 21.0.12+1, `-Xms32m -Xmx4096m`, `--max-work 100000000`, timeout externo
de 300 s, parser direto/FIXED/UTF-8 e GNU time para RSS. Processos sequenciais,
JVM nova por caso, versão anterior antes da otimizada, sem limpeza do cache de
arquivos. Uma amostra por versão/caso; não são intervalos de confiança.

| Caso | Tempo anterior → novo | RSS anterior → novo | Trabalho anterior → novo |
|---|---:|---:|---:|
| AND608 | 5,02 → 1,72 s | 490,4 → 237,0 MiB | 555.928 → 4.821 |
| AND1216 | 22,35 → 2,97 s | 936,1 → 362,2 MiB | 2.221.544 → 9.685 |
| Overlap400 | 3,12 → 1,62 s | 432,0 → 222,7 MiB | 497.224 → 7.326 |
| Overlap900 | 9,18 → 2,47 s | 1.282,5 → 384,6 MiB | 2.469.724 → 16.826 |
| Overlap1800 | 42,09 → 4,37 s | 3.078,9 → 514,8 MiB | 9.800.224 → 33.926 |
| OR1216, solver anterior | 10,18 → 10,13 s | 961,6 → 870,8 MiB | 1.485.530 → 1.485.530 |

Todas as seis saídas ficaram byte-idênticas, incluindo tipo, nome, programa,
ordenação e proveniência. Na matriz ampliada inteira, **30/30 PASS** e **30/30
saídas byte-idênticas** à auditoria anterior; 24 dessas comparações usam a
evidência histórica, além das seis comparações novas. Os hashes das fontes
foram verificados. O tempo total novo foi 107,30 s; o maior RSS foi 1.793,8 MiB,
no sintético com 117 mil atribuições.

Ao dobrar overlap900 para overlap1800, o trabalho anterior cresceu 3,97 vezes;
o novo cresceu 2,02 vezes. AND608 para AND1216 passou de crescimento de 4,00
vezes para 2,01 vezes. Em overlap1800, o tempo interno de dataflow, que inclui
a preparação de suas projeções, caiu de 37,30 s para 0,403 s. O tempo externo
continua pagando parsing, binding, construção do controle e publicação.

O número de contextos em overlap1800 aumentou de 1.802 para 5.363 porque os
resumos têm seus próprios contextos. O custo caiu porque esses contextos
compartilham as caudas, em vez de manter uma cópia dos estados de toda a faixa
por entrada. Contar contextos isoladamente não mede esse trabalho.

## Correção e limites

Os 290 testes do analisador passaram, sem falhas/erros/skips; incluem dez
testes novos com endpoints diferentes, observações internas, entradas distintas,
valores exclusivos do chamador, aliases, escritas parciais, incerteza de CALL,
branches, fases de repetição, recursão e fallback de handler de arquivo mesmo
sem file points. Um teste compara 24 programas variados
com seed fixa ao solver anterior; outro verifica crescimento em 80/160/320
parágrafos. Dependências completas e diagnósticos são comparados diretamente.
FAST herdado: 856 testes aprovados, sem skips. O JAR passou o smoke isolado.

Os 20 insumos originais foram reexecutados: 20/20 resultados esperados, incluindo
os dois negativos de sintaxe. Não são 20 sucessos de análise: são 18 fontes
válidas e duas rejeições corretas sem publicação. Os recursos foram os mesmos
4 GiB/100 milhões/300 s, em 16,16 s e com maior RSS de 336,5 MiB.
CardDemo foi reexecutado integralmente: **73/73 fontes**, **815 relações iguais**,
nenhuma ausente ou adicional, **73 JSONs byte-idênticos** aos anteriores e os
mesmos códigos de saída (26 sem incompletude, 47 PARTIAL). Usou o perfil de
comparação anterior de 768 MiB e levou 119,17 s, com maior RSS de 464,1 MiB.
Os 185 hashes de fontes/includes também foram conferidos pelo runner.

A família OR contém GO TO e mantém o crescimento anterior. O sintético linear
de 117 mil atribuições não tem intervalos de PERFORM para compartilhar; seu
custo de frontend/preparação continua presente. Seu sucesso não demonstra
esta otimização, e ele não é o programa corporativo original indisponível.
As diferenças de RSS fora da família otimizada, entre amostras isoladas com
heap grande, não estabelecem ganho ou regressão de memória do frontend.

O ganho não é uma garantia universal de tempo linear: a quantidade de estados
de entrada distintos, de endpoints e de fatos relevantes pode crescer.
Os limites de recursos e os estados PARTIAL existentes permanecem explícitos.
Não foram alterados orçamento padrão, candidatos esperados, parser, frontend,
domínio de valores ou formato de saída. A suíte completa histórica não foi
reexecutada; os gates novos cobrem o solver alterado, FAST, fixtures e corpus.

## Reprodução

Preserve o JAR anterior antes do build da branch. Todos os diretórios de saída
devem ser novos; os fontes/geradores originais são somente leitura.

```sh
python3 -B scripts/harness/lean.py fast
# FAST limpa target; construir o JAR depois dele.
mvn -Dtest=DependencySuffixTest,DependencyAnalyzerTest,DependencyRegressionTest,DependencySourceTest,DependencyEnvironmentTest,DependencyResourceTest package
python3 -B benchmark/run-explosion-review.py benchmark/results/before-new-label --jar /caminho/ao/jar-anterior.jar --heap 4096 --max-work 100000000 --timeout 300 --extended --case and608 --case and1216 --case or1216 --case overlap-dynamic400 --case overlap-dynamic900 --case overlap-dynamic1800
python3 -B benchmark/run-explosion-review.py benchmark/results/after-new-label --heap 4096 --max-work 100000000 --timeout 300 --extended
python3 -B benchmark/compare-suffix-results.py benchmark/results/before-new-label benchmark/results/after-new-label benchmark/results/comparison-new-label.json
python3 -B benchmark/run-resource.py benchmark/results/original20-new-label --heap 4096 --max-work 100000000 --timeout 300
python3 -B benchmark/run-carddemo.py ../artefatos-e2e/carddemo-scanner-discovery-20261003/results.json benchmark/results/carddemo-new-label
```

Logs, fontes derivados, comandos, JSONs completos e relatórios brutos permanecem
em `benchmark/results/suffix-*-20261008*` e `.tmp/suffix-*.log`, ignorados pelo
Git. Os resultados versionados guardam os hashes dessas evidências e dos JARs.
