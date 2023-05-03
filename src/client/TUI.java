//package client;
//
//import java.util.Scanner;
//
//public class TUI {
//
//    static final String NAME = "!name ";
//    static final String HELP = "!hlp";
//    static final String SUBMARINES = "!submarines";
//    static final String TABLE = "!table";
//    static final String EXIT = "!exit";
//
//
//    String menu =
//            """
//                    COMMANDS:
//                    !name [NAME] ............ Enter your name by typing !name, a spacebar, then a name of your choice.
//                    !help ................... Print out these commands if you forget what there are.
//                    !submarines ............. Display all current online submarines (nodes) in the chat.
//                    !table .................. Print your routing table
//                    !exit ................... Exit the program and any chat you are in.""";
//
//    public void run() {
//
//        System.out.println(menu);
//
//        boolean exit = false;
//
//        while (!exit) {
//
//            Scanner input = new Scanner(System.in);
//            String line = input.nextLine();
//
//            String[] split = line.split("\\s+");
//            String command = split[0];
//            String param = split[1];
//
//            switch (command) {
//                case NAME -> {
//                    if (!param.equals("")) {
//                        System.out.println("Ok, your name is: " + param + ".");
//                    } else {
//                        System.out.println("Provide a name, buddy.");
//                    }
//                }
//                case HELP -> System.out.println(menu);
//                case SUBMARINES -> {
//                    System.out.println("You are connected with the following submarines: ");
//                    }
//
//
//
//                case TABLE -> {
//                    System.out.println("This is your forwarding table:");
//                }
//                case EXIT -> exit = true;
////                default -> {
////
////                }
//            }
//        }
//    }
//}
