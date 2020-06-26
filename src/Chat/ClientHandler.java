package Chat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private MyServer myServer;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String name;

    public String getName() {
        return name;
    }

    public ClientHandler(MyServer myServer, Socket socket) {
        this.myServer = myServer;
        this.socket = socket;
        this.name = "";

        try {
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            new Thread(()-> {
                try {
                    authenticate();
                    readMessages();
                } catch (IOException ex) {
                    ex.printStackTrace();
                } finally {
                    closeConnection();
                }
            }).start();
        } catch (IOException ex) {
            throw new RuntimeException("Client creation error");
        }
    }

    private void closeConnection() {
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        myServer.unsubscribe(this);
        myServer.broadcast("User " + name + " left", true);
    }

    private void readMessages() throws IOException {
        while (true) {
            if (in.available()>0) {
                String message = in.readUTF();
                System.out.println("From " + name + ":" + message);
                if (message.equals("/end")) {
                    sendMsg("/end");
                    closeConnection();
                    return;
                }
                if (message.startsWith("/w ")) {
                    String[] parts = message.split("\\s");
                    String realMessage = message.substring(message.indexOf(" ", message.indexOf(" ") + 1));
                    myServer.sendDirect(parts[1],name+ ": "+ realMessage);
                }
                // Для смены ника нужно отправить сообщение с префиксом /alternick
                if (message.startsWith("/alternick ")) {
                    // Парсим строку для вычленения нового ника (он должен быть без пробелов, иначе будет урезан до первого пробела)
                    String[] parts = message.split("\\s");
                    String newNick = parts[1];
                    // Если такого ника в БД пользователей нет, то производим смену ника
                    if (!myServer.getAuthService().isNickExist(newNick)) {
                        if (myServer.getAuthService().changeNick(name, newNick)) {
                            // Рассылаем всем в чате сообщение о том, что у пользователя теперь новый ник
                            myServer.broadcast(name + " now is " + newNick, true);
                            sendMsg("/yournick " + newNick);
                            // И непосредственно самому объекту пользователь меняем ник
                            name = newNick;
                        } else {
                            sendMsg("Error of change nick. Try again");
                        }
                    // Если ник уже занят, сообщаем пользователю об этом
                    } else {
                        sendMsg("Nick " + newNick + " is already in use. Enter another nick.");
                    }
                } else {
                    myServer.broadcast(name + ": " + message, true);
                }
            }
        }
    }

    private void authenticate() throws IOException {
        while(true) {
            if (in.available()>0){
                String str = in.readUTF();
                if (str.startsWith("/auth")) {
                    String[] parts = str.split("\\s");
                    String nick = myServer.getAuthService().getNickByLoginAndPwd(parts[1], parts[2]);
                    if (nick != null) {
                        if (!myServer.isNickLogged(nick)) {
                            System.out.println(nick + " logged into chat");
                            name = nick;
                            sendMsg("/authOk " + nick);
                            myServer.broadcast(nick + " is in chat", true);
                            myServer.subscribe(this);
                            return;
                        } else {
                            System.out.println("User " + nick + " tried to re-enter");
                            sendMsg("User already logged in");
                        }
                    } else {
                        System.out.println("Wrong login/password");
                        sendMsg("Incorrect login attempted");
                    }
                }
            }

        }
    }

    public void sendMsg(String s) {
        try {
            out.writeUTF(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}