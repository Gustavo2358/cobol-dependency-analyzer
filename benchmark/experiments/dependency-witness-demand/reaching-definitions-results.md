# Resultado: reaching definitions esparso

Registro histórico anterior à correção do controle. A qualificação posterior
que remove os 11 adicionais do CardDemo está em
[control-fix-results.md](control-fix-results.md).

Implementação local `332abaf`, branch `experiment/dependency-witness-demand`.
Baseline main `52c1b82dc6accbb615818cf5b5298843a85b0f01`; JAR SHA-256
`6bd96f31911976c097c9fb47377587c11bf3ebe6dc60305190104314bc39b036`. Build final: `build-06`.

O experimento resolveu todos os 109 casos da baseline, sem recusar construções
e sem perder nomes de dependências. O protótipo anterior admitia 24/109. A nova
versão mantém um só solver experimental, removendo WitnessFlow e o Fit escalar.
Não há fallback ao solver de contextos, whitelist de construções ou corte de profundidade.

## Preservação e preço da aproximação

| Grupo | Casos concluídos | Nomes perdidos | Nomes adicionais | Localizações alteradas |
|---|---:|---:|---:|---:|
| Standalone | 36/36 | 0 | 30 | 32 |
| CardDemo | 73/73 | 0 | 11 | 0 |
| Adversariais | 25/25 | 0 | 4 | 0 |

CardDemo: 815 dependências da baseline preservadas, 826 no experimento. Os 11
acréscimos são 9 referências ao próprio programa e 2 ocorrências de CARDAIX.
São acréscimos relativos à baseline. A [auditoria posterior dos fontes](carddemo-additions-audit.md)
identificou caminhos locais artificiais nos seis casos distintos: duas ocorrências
de CARDAIX são READs inalcançáveis; as autorreferências resultam de retornos/travessias
inventados. Quatro programas recebem o nome de origem de fora, portanto não se
exclui a autorreferência em qualquer ambiente externo. As fixtures negativas confirmam falsos positivos por perder
correlações de condição/chamador e por resumir posições de OCCURS.
As 32 mudanças de localização em standalone são antecipações por esses fluxos
aproximados. Nenhuma localização mudou em CardDemo.

Os 25 adversariais cobrem kill, BEFORE, código inalcançável, PERFORM sem retorno,
cópias/ciclos/larguras, CORRESPONDING, grupos, INITIALIZE, funções, substrings,
OCCURS, escrita desconhecida, USE AFTER ERROR, CICS condition e ABEND. Dois
testes metamórficos inserem 128 CONTINUE: nomes iguais e nenhum crescimento
proporcional das definições. Oráculos de nomes/localizações e extras estão em
`fixtures/expected.json`; foram requalificados em `oracle-requalification`.

## Escala das caixinhas

N é o número de caixinhas por hub; há dois hubs. Heap máximo 512 MiB, RSS
limitado a 768 MiB, reserva do host 2 GiB, timeout 60 s, max-work 100M.

| N | Main (baseline reutilizada) | Experimento | RSS MiB | Trabalho instrumentado |
|---:|---:|---:|---:|---:|
| 32 | 3.33 s | 1.11 s | 117.5 | 5,638 |
| 64 | 13.13 s | 1.37 s | 126.2 | 11,206 |
| 128 | timeout 60 s | 1.46 s | 133.1 | 22,342 |
| 512 | não reexecutada | 2.78 s | 235.0 | 89,158 |
| 2048 | não reexecutada | 7.43 s | 586.1 | 356,422 |

Ao quadruplicar N=512 para 2048, o trabalho passa de 89.158 para 356.422
(aproximadamente 4x); definições, arestas e candidatos também crescem nessa
proporção. Esse contador mede operações do solver, não uma prova assintótica
nem todas as instruções JVM. Tempos são execuções individuais, sem análise estatística.

A família dynamic-only substitui os CALLs literais por CONTINUE. N=8 e N=32
foram comparados com main, sem perda, acréscimo ou mudança de localização.
N=128/512/2048 usam oráculo independente de 129/513/2049 nomes esperados,
encontrados exclusivamente através de CALL TARGET-PGM. Não há CALL literal.
N=2048 terminou em 7.48 s, 545.5 MiB RSS.

A família negative-bound exclui o VALUE DEAD por uma sobrescrita antes do hub.
N=32/128/512/2048 termina com exatamente SAME. Não é necessário inventariar
nomes inalcançáveis nem repetir sua procura por query.

A baseline de escala foi reutilizada: JAR, fonte SHA-256, parâmetros da JVM e
limites iguais. É a medição main de `witness-demand-20261009/scale-small`; não
se atribui uma nova execução a esses números. N grandes não foram novamente
submetidos ao solver antigo, cujo timeout já está registrado.

## Correções encontradas durante a validação

O primeiro teste com offset {1,5} perdeu NEXT: a operação antiga de substring
exigia singleton. Agora os offsets conhecidos são avaliados localmente em fluxo
e unidos ao restante conservador. Não são enumerados chamadores ou caminhos.
A avaliação name-based de CORRESPONDING também precisa das folhas da origem,
em vez de somente um snapshot do grupo. USE AFTER ERROR revelou a necessidade
de conservar o retorno do final aberto de um declarativo, publicado pelo frontend
como UNKNOWN_LOCAL. Esses casos tiveram RED antes de sua correção; logs intactos.

## Gates e limites

FAST da aplicação inalterada: 1093 testes, 142 suítes, zero falhas/erros/skips,
executado novamente em 55.98 s. Esse FAST valida a aplicação principal; não se faz passar
seus testes pelo overlay. O overlay foi validado por CLI/oráculos/metamórficos
e pelos 109 casos. Com max-work=1, exit 2 RESOURCE_LIMIT conservou o output
anterior byte a byte. Nenhuma run final terminou em OOM ou proteção de memória.

Duas campanhas preliminares foram interrompidas pelo host abaixo da reserva de
2 GiB, com RSS de 67 e 186 MiB nos processos de teste. Não foram contadas como
PASS; campanhas finais completas ficam separadas. Builds com erro, fixture STRING
inicial inválida, NPE de lookup e perdas semânticas preliminares também permanecem
no diretório bruto. Não foram alterados para obter PASS.

A aproximação relaxa predicados, pareamento de retornos e índices de tabelas.
Tudo continua PARTIAL e é explicitamente diagnosticado. Não há prova universal
de ausência de perdas; a preservação relatada é a das baselines exercitadas.
Também não há garantia de complexidade linear em todo programa: muitas variáveis
atravessando muitas junções e produtos locais de strings/endereços ainda podem
custar mais. A família que reproduzia nossa parede foi superada sem aumentar heap.

A produção, POM e scripts não mudaram. O overlay ignora os solvers globais antigos
e usa somente seus efeitos/transformações locais; a extração desses serviços
para uma substituição de produção ainda não foi feita. Não foi aberto/pushado PR.
O experimento 1 continua em sua branch, sem alterações.

Evidência bruta: `benchmark/results/reaching-definitions-20261009/`. Hashes,
comandos, probes e diferenças detalhadas: `reaching-definitions-results.json`.
