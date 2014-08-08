package zebraprinterclient;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;

public class ZebraPrinterClient {

	private static final String CONFIGURATION_FILE = "Totem.properties";
	private static String totemId = "";
	private static String printerName = "";
	private static int printerIntervalMs = 1000;
	private static String databaseIp = "";
	private static String databaseName = "";
	private static String databaseUser = "";
	private static String databasePassword = "";

    public PrintService ActivePrinterService;
    public int verboselevel;

    public PrintService getActivePrinterService() {
        return ActivePrinterService;
    }

    public void setActivePrinterService(PrintService ps) {
        ActivePrinterService = ps;
    }

    public PrintService selectActivePrinterService(String nameservice) {
        ActivePrinterService = selectPrinter(nameservice);
        return ActivePrinterService;
    }

    public PrintService selectPrinter(String sprintername) {
        PrintService ps = null;
        // Search for an installed zebra printer...
        // is a printer with "zebra" in its name
        try {
            String sPrinterName = null;
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);

            for (int i = 0; i < services.length; i++) {
                if (sprintername.equalsIgnoreCase(services[i].getName()) == true) {
                    ps = services[i];
                }
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (ps == null) {
            System.out.println("Zebra printer is not found.");

        }
        return ps;
    }

    public boolean printAText(String text, String codetext, boolean buildthesource, int verbose)
    {
        boolean b1 = false;
        if (ActivePrinterService != null) {
        	// System.out.println("Found printer: " + sPrinterName);
            DocPrintJob job = ActivePrinterService.createPrintJob();
            String s;
            // Prepare string to send to the printer
            if (buildthesource == true) {
                s = buildPrintableText(text, codetext, verbose);
                System.out.println("Frame di stampa zebra:" + s);
            } else {
                s = text;
            }
            if (s != null) {

                byte[] by = s.getBytes();
                DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
                // MIME type = "application/octet-stream",
                // print data representation class name = "[B" (byte array).
                Doc doc = new SimpleDoc(by, flavor, null);

                try {
                    if (verboselevel >= 2) {
                        System.out.println("Pronti alla stampa di" + s);
                    }
                    job.print(doc, null);
                    if (verboselevel >= 2) {
                        System.out.println("Stampa inviata");
                    }
                } catch (Exception e) {
                }
            }
        } else {
        }
        return b1;

    }

    public String buildPrintableText(String sourcetext, String sourcecode, int verbose)
    {
        String retS = null;
        if (sourcetext != null) {
            retS = "R0,0\n" + // Set Reference Point
                    "N\n" + // Clear Image Buffer
                    "ZB\n" + // Print direction (from Bottom of buffer)
                    "Q122,16\n" + // Set label Length and gap
                    "A160,2,0,3,1,1,N,\"" + sourcetext
                    + "\"\n"
                    + "B160,30,0,1A,2,7,50,N,\"" + sourcecode + "\"\n"
                    + "P1\n"; // Print content of buffer, 1 label

        }
        return retS;
    }

    private static void printLabel(String text)
    {
        ZebraPrinterClient tzp = new ZebraPrinterClient();
        tzp.selectActivePrinterService(printerName);
        tzp.printAText(text, "", false, 2);
    }
    
    private static String getNextPrinting(String id_totem, Connection conn)
    {
//      System.out.println("getNextPrinting "+id_totem);

//      Connection conn = null;
        CallableStatement cstmt = null;

        try {

//          conn = getConnection();

            String sProc = "call P_Service_Printing_Next(?)";
            cstmt = conn.prepareCall(sProc);
            cstmt.setString(1, id_totem);

            ResultSet rs = cstmt.executeQuery();
            
            String result = "";
            while (rs.next()){
                    result = rs.getString(1);
            }
            
            if (!result.equals(""))
            	System.out.println("P_Service_Printing_Next executed: " + result);

            return result;

        } catch (Exception e) {
                e.printStackTrace();
        } finally {

                try {
//                      conn.close();
                        cstmt.close();
                } catch (SQLException e) {
                        e.printStackTrace();
                }

        }
        return "";
    }

    private static Connection getConnection() {

            try {
                Connection conn = DriverManager.getConnection("jdbc:mysql://"+databaseIp+
                                                                          "/"+databaseName+
                                                                     "?user="+databaseUser+
                                                                     "&password="+databasePassword);
                return conn;

            } catch (Exception e) {
//            	e.printStackTrace();
            }
            return null;

    }
    
    public static void main(String[] args)
    {
		// read properties file
		Properties properties = new Properties();
		try {
		    properties.load(new FileInputStream(CONFIGURATION_FILE));
		} catch (IOException e) {
			System.err.println("Warning: error loading configuration file " + CONFIGURATION_FILE);
		}
        totemId = properties.getProperty("TOTEMID");
        printerName = properties.getProperty("PRINTERNAME");
        printerIntervalMs = Integer.parseInt(properties.getProperty("PRINTERINTERVALMS"));
        databaseIp = properties.getProperty("DATABASEIP");
        databaseName = properties.getProperty("DATABASENAME");
        databaseUser = properties.getProperty("DATABASEUSER");
        databasePassword = properties.getProperty("DATABASEPASSWORD");
        
        System.out.println("ZebraPrinter Client for Totem "+totemId);
        System.out.println("================================");
        
        Connection conn = null;

        while (true)
        {
            try {
				if(conn==null || !conn.isValid(0))
				{
					System.out.print("connecting...");
					conn = getConnection();
					if(conn!=null && conn.isValid(0))
						System.out.println("connected");
					else
						System.out.println("connection timed out");
				}
				if(conn!=null && conn.isValid(0))
				{
					String text = getNextPrinting(totemId, conn);
		            if (!text.equals(""))
		                printLabel(text);
				}
				
				Thread.sleep(printerIntervalMs);
				
			} catch (SQLException e) {
				e.printStackTrace();
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }
        }
        
//        System.out.println("Zebra Print testing!");
//
//        // Prepare date to print in dd/mm/yyyy format
//        Date now = new Date();
//        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
//        String dateString = format.format(now);
//
//        // Search for an installed zebra printer...
//        // is a printer with "zebra" in its name
//        try {
//            PrintService psZebra = null;
//            String sPrinterName = null;
//            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
//            for (int i = 0; i < services.length; i++) {
//                if (printerName.equalsIgnoreCase(services[i].getName()) == true) {
//                    psZebra = services[i];
//                }
//                break;
//            }
//            if (psZebra == null) {
//                System.out.println("Zebra printer is not found.");
//                return;
//            }
//
//            System.out.println("Found printer: " + sPrinterName);
//            DocPrintJob job = psZebra.createPrintJob();
//
//            // Prepare string to send to the printer
//            String s =  "R0,0\n" +   // Set Reference Point                                                             
//                        "N\n" +         // Clear Image Buffer                                                             
//                        "ZB\n" + // Print direction (from Bottom of buffer)
//                        "Q122,16\n" +  // Set label Length and gap
//                        "A160,2,0,3,1,1,N,\"DATA: " + dateString + " - CARUGATE\"\n" +
//                        "B160,30,0,1A,2,7,50,N,\"612041600021580109\"\n" +                            
//                        "A160,92,0,1,1,1,N,\"AIA AGRICOLA IT.ALIMENT.S - 594679/VR                       \"\n" +
//                        "P1\n";   // Print content of buffer, 1 label
//
//            byte[] by = s.getBytes();
//            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
//            // MIME type = "application/octet-stream",
//            // print data representation class name = "[B" (byte array).
//            Doc doc = new SimpleDoc(by, flavor, null);
//
//            System.out.println("Pronti alla stampa");
//            job.print(doc, null);
//            System.out.println("Stampa inviata");
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

    }

}