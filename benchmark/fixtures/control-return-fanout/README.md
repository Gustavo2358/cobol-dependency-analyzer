# Relações de chamada e saída no solver de controle

Estas fixtures provocam acúmulo de `DependencyControl.Returned`, independente
da explosão de vetores de valores. Veja [medições e profiling](../../control-return-fanout-20261009.md)
e os [oráculos exatos com hashes](expected.json).

Dois hubs renovam seus seletores via ACCEPT, fazem GO TO DEPENDING ON para as
caixinhas ou FINAL-BOX, e repetem quando o seletor está fora do intervalo. Cada
caixinha faz CALL literal, pode sair por EXIT PARAGRAPH, pode executar PERFORM
da próxima caixinha do outro hub, e retorna por GO TO ao outro hub. FINAL-BOX
termina por GOBACK. As saídas explícitas espalham fronteiras distintas pelos
resumos; entregas dessas saídas podem gerar chamadas internas de continuação.

| Fonte | Propósito |
| --- | --- |
| external-8.cbl | Renova também a flag externa; mantém o padrão dinâmico e todas as alternativas. |
| fixed-12.cbl | MOVE 0 na flag isola o controle estrutural, com três contextos de valores. |
| fixed-16.cbl | Mais de um milhão de pares retidos, ainda com três contextos de valores. |
| fixed-24.cbl | OOM reproduzido no JAR original com heap 256 MiB, durante o solver de controle. |
| no-exit-12.cbl | Mutação de fixed-12: troca somente EXIT PARAGRAPH por EXIT. |
| renamed-call-12.cbl | Mutação de fixed-12: troca PGM00000 por OTHER000 nos dois hubs. |
| escape-ablation-sentinel.cbl | Original encontra AFTER e ESCAPED; desligar a reinvocação perde ESCAPED. |

Em fixed, a flag não é renovada. O MOVE fecha seu valor no início da execução;
VALUE na declaração, sozinho, conserva remainder no domínio inicial do produto.
As condições falsas permanecem no resumo estrutural, mas o solver de valores
as elimina. Isso permite medir o custo do controle sem acumular estados de dados.
A mutação no-exit preserva os candidatos na variante fixed; não é uma alteração
proposta para programas reais. A mutação renamed-call deve mudar o conjunto de
candidatos, verificando que o oráculo não se limita a contagens.

Todos os fontes têm formato FIXED, até 72 colunas. O número no nome significa
caixinhas **por hub**; há duas vezes esse número de parágrafos de caixinha.
Não há OCCURS: os casos concluídos têm uma única passagem de demanda.

A sentinela e os [experimentos de ablação](../../escape-ablation-20261009.md)
investigam o trabalho restante. Ela não pertence ao oráculo histórico expected.json;
seus candidatos originais e sua perda diagnóstica estão no relatório de ablação.

Este testemunho reproduz o mecanismo de relações e GC observado na evidência
corporativa, em escala reduzida. Não demonstra que o fonte privado contém EXIT
PARAGRAPH nem reproduz os 70 minutos sem OOM.
