# Servidor do clube — guia para a secretaria

[Italiano](GUIDA_SERVER.md) · [English](GUIDE_SERVER.en.md) · [Français](GUIDE_SERVER.fr.md) · [Deutsch](GUIDE_SERVER.de.md) · [Español](GUIDE_SERVER.es.md) · **Português**

O servidor do clube é opcional: só é preciso se quiserem que os jogadores reservem as quadras sozinhos, pelo próprio telefone. Sem servidor o app funciona do mesmo jeito: as reservas são anotadas pela secretaria.

Há três modos de usá-lo. Escolham o que precisam:

| Modo | Os jogadores reservam… | O que é preciso |
|---|---|---|
| **A. Só no clube** | quando estão no Wi-Fi do clube | o PC do servidor ligado |
| **B. Pela internet com Tailscale** | de qualquer lugar | além disso, uma conta gratuita do Tailscale (10 minutos, uma vez só) |
| **C. Pela internet com o roteador** | de qualquer lugar | além disso, alguém que saiba configurar o roteador do clube |

O conselho: comecem pelo modo **A**. Se quiserem reservas de casa, passem ao **B**: não é preciso mexer no roteador.

---

## 1. Ligar o servidor (para todos os modos)

É preciso um PC antigo (ou um Raspberry Pi preparado por um técnico) e o **pen drive TSM-SERVER**.

1. Liguem o PC à rede do clube, de preferência com cabo. Num notebook sem cabo o Wi-Fi também serve: na primeira vez, quando a tela disser **No network**, digitem `user`, Enter, `live`, Enter e depois `sudo nmtui` (depois de entrar, a tela lembra todos os comandos); escolham **Activate a connection**, a rede do clube e digitem a senha. Fica salva no pen drive: nas vezes seguintes conecta sozinho. Para sair: **Quit** e depois `exit`.
2. Coloquem o pen drive e liguem o PC. Se o Windows iniciar, reiniciem, apertem a tecla do menu de inicialização (normalmente F12, F9 ou Esc: a tela mostra ao ligar) e escolham o pen drive USB.
3. Depois de cerca de um minuto a tela mostra:

   ```
   TSM – Server del circolo / Club server
   http://192.168.1.50:8080
   Codice di abbinamento / Pairing code: 123456
   ```

   Os números de vocês serão outros. Anotem o **endereço** (a linha `http://…`) e o **código**.
4. Deixem o PC ligado. Não é preciso entrar nem digitar nada: o servidor trabalha sozinho. Se desligarem o PC, os jogadores não reservam até ele voltar a ligar; os dados ficam no pen drive.

Se a tela disser **WARNING: no persistence partition**, o pen drive não foi bem preparado: os dados se perderiam ao desligar. Mandem refazer.

## 2. Emparelhar o telefone da secretaria (uma vez só)

1. Com o telefone no **Wi-Fi do clube**, abram o TSM e vão em **Quadras › Servidor do clube**.
2. Em **Endereço do servidor** escrevam o endereço da tela do PC; em **Código de emparelhamento**, o código.
3. Toquem em **Emparelhar**. O código vale uma vez só: a tela do PC agora diz **Segreteria abbinata / Office paired**.

Se um dia trocarem de telefone, é preciso um código novo: peçam ao técnico para entrar no PC do servidor e digitar `sudo tsm-reset-admin`.

## 3. Modo A: reservas no clube

Ainda em **Quadras › Servidor do clube**:

1. Liguem **Reservas online abertas**.
2. Escolham as **Quadras reserváveis online** e, se quiserem, as regras (quantos dias antes, quantas reservas por jogador…).
3. Toquem em **Enviar agora**: a agenda vai para o servidor.
4. Para cada jogador: abram a ficha dele e toquem em **Convite para reservar online**. O jogador aponta a câmera para o QR e reserva pela página do clube (no iPhone: Compartilhar › Adicionar à Tela de Início, e ela abre como um app).

Pela secretaria no PC é igual: **Sócios**, abram o sócio e cliquem em **Convite para reservar online**. Além do QR há **Copiar o link**, **Enviar pelo WhatsApp** e **Enviar por e-mail**: a mensagem abre já escrita e quem envia são vocês.

As reservas dos jogadores chegam sozinhas à agenda da secretaria, marcadas **online**.

## 4. Modo B: pela internet com Tailscale

Tailscale é um serviço que liga o servidor à internet "por dentro", sem abrir portas no roteador. Funciona também com conexões sem endereço público (muitas linhas 4G/5G e sem fio). É preciso uma conta gratuita.

**Antes:** o servidor precisa ter internet (a rede do clube sai para a internet) e o telefone da secretaria já deve estar emparelhado (capítulo 2).

1. Em **Quadras › Servidor do clube**, no quadro **Acesso pela internet sem mexer no roteador**, toquem em **Ativar**.
2. Esperem alguns minutos: na primeira vez o servidor baixa o Tailscale (**O servidor está preparando a conexão**).
3. Aparece **Abrir o Tailscale**: toquem. A página do Tailscale abre no navegador:
   - se a conta já existe, entrem;
   - se não, criem uma: entra-se com uma conta que vocês já têm (por exemplo Google, Microsoft ou Apple). Melhor a conta do clube do que a pessoal de quem cuida da secretaria.
   - O Tailscale pede para conectar um dispositivo chamado **tsm-circolo**: confirmem.
4. Voltem ao TSM. Logo aparece **Último passo: abra o Tailscale e confirme a ativação do Funnel**. Toquem de novo em **Abrir o Tailscale** e confirmem (Funnel é a função que torna o servidor acessível pela internet).
5. Voltem ao TSM: aparece **Ativo: https://tsm-circolo.….ts.net**. Esse é o endereço pela internet, e o TSM o coloca sozinho em **Endereço pela internet**. A partir de agora os convites funcionam também de casa.
6. **Uma coisa para fazer logo**: a cada seis meses o Tailscale pede para entrar de novo. Para evitar isso, num computador vão em **login.tailscale.com**, achem **tsm-circolo** em **Machines**, abram o menu (os três pontos) e escolham **Disable key expiry**.

Para desligar o acesso pela internet: **Desativar**. As reservas no clube (modo A) continuam funcionando.

Enquanto o acesso está ativo, a tela do PC do servidor mostra também a linha **Da internet / From the internet**.

O plano gratuito do Tailscale é pensado para uso pessoal e pequeno: antes de usá-lo para o clube, deem uma olhada nas condições no site deles.

**Se algo der errado**
- **Não funcionou: verifique se o servidor tem internet**: o PC do servidor não sai para a internet. Verifiquem o cabo ou o Wi-Fi do clube e toquem de novo em **Ativar**.
- **Para usar isso, atualize o servidor**: o pen drive é antigo, sem Tailscale. Mandem refazer com a imagem nova.
- Se depois de meses o endereço pela internet parar de funcionar, abram **Quadras › Servidor do clube**: se aparecer de novo **Abrir o Tailscale**, entrem outra vez com a conta de vocês.

## 5. Modo C: pela internet com o roteador

É o modo clássico: não depende do Tailscale, mas é preciso alguém que saiba configurar o roteador do clube, e a linha precisa ter um endereço público.

Para passar ao técnico:
1. Um nome gratuito em **duckdns.org** (ex. `tcrossi.duckdns.org`) e o token dele.
2. No roteador, as portas **80** e **443** encaminhadas para o PC do servidor.
3. No PC do servidor, entrando com o usuário `user` e a senha `live` (a trocar com `passwd`):

   ```
   sudo /opt/tsm-server/kit/setup-https.sh tcrossi.duckdns.org --duckdns TOKEN
   ```

4. No TSM, em **Endereço pela internet**, escrevam `https://tcrossi.duckdns.org`.

Para saber se a linha tem endereço público: comparem o endereço WAN da página do roteador com o de um site tipo "qual é meu IP". Se forem diferentes, ou se o do roteador começar com `100.64`–`100.127`, o modo C não funciona: usem o **B**.

## 6. Lembretes para a secretaria

- O PC do servidor fica ligado; o pen drive não se tira.
- De vez em quando façam uma cópia em **Quadras › Cópia da secretaria**: uma cópia fica também no servidor.
- Para parar as reservas por um tempo (manutenção, fechamento), desliguem **Reservas online abertas**: os jogadores veem a agenda mas não reservam.
- Um jogador que não deve mais reservar: na ficha dele, **Retirar o acesso online**.
- Um aviso para todos (quadras fechadas, um torneio, uma festa): **Servidor do clube › Mural do clube**, ou **Reservas online** pelo PC. Chega aos jogadores com uma notificação e fica 30 dias. Em **Chats dos anúncios** vocês podem tirar as mensagens fora de lugar nos chats de Procuro parceiro.
