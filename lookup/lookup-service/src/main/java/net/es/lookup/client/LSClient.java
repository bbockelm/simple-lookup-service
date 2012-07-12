import java.io.*;
import java.net.*;
import java.util.*;




public class LSClient{
	private String urlStr = null;  
	private String urlStrs = null;

	public LSClient(String url,String urls){
		this.urlStr=url;
		this.urlStrs=urls;
	}

	public  String getDataOnServer(){
		String returnString="";
//		DataInputStream in=null;
		try{  

			URL url = new URL(this.urlStrs);  
			HttpURLConnection connection= (HttpURLConnection)url.openConnection();   
			connection.setAllowUserInteraction(true);  
			connection.setRequestMethod("GET");  
			connection.setDoInput(true);
			connection.setDoOutput(true);  
			connection.setUseCaches(false);  
			System.out.println("Msg: "+ connection.getResponseMessage());
			System.out.println("Error code: "+ connection.getResponseCode());
			
			DataInputStream in = new DataInputStream (connection.getInputStream ()); 
//			BufferedReader in = new BufferedReader(
//					new InputStreamReader(connection.getInputStream()));

			String inputLine;
			while (null!=((inputLine = in.readLine()))){
//				System.out.println(inputLine);
			    returnString +=inputLine;
			}
			in.close();

		}catch(Exception e){  
			e.printStackTrace();  
		}  
		System.out.println("============"+returnString);

		return returnString;
	}



	public String getService(String recorduri){
		String returnString="";
		String urlStr=null;
		if (recorduri!=null)
			urlStr = this.urlStr+recorduri;  

		try{  
			URL url = new URL(urlStr);  

			HttpURLConnection connection= (HttpURLConnection)url.openConnection();  
			connection.setAllowUserInteraction(true);  
			connection.setRequestMethod("GET");  
			connection.setDoInput(true);
			connection.setDoOutput(true);  
			connection.setUseCaches(false);
			System.out.println("Msg: "+ connection.getResponseMessage());
			System.out.println("Error code: "+ connection.getResponseCode());

			DataInputStream in = new DataInputStream (connection.getInputStream ()); 
//			BufferedReader in = new BufferedReader(
//					new InputStreamReader(connection.getInputStream()));

			String inputLine;
			while (null!=((inputLine = in.readLine()))){
//				System.out.println(inputLine);
			    returnString +=inputLine;
			}
			
			in.close();

		}catch(Exception e){  
			e.printStackTrace();  
		}  
		System.out.println("============"+returnString);
		return returnString;
	}


	public String getServiceKey(String recorduri, String key){
		String urlStr=null;
		String returnString="";
		if(recorduri!=null){
			if (key!=null){
				//				if(key.equals("record-service-domain")||
				//						key.equals("record-service-locator")||
				//						key.equals("record-service-type")||
				//						key.equals("record-type"))
				urlStr = this.urlStr+recorduri+"/"+key;  
			}
			else
				urlStr=this.urlStr+recorduri;
		} 
		try{  
			URL url = new URL(urlStr);  

			HttpURLConnection connection= (HttpURLConnection)url.openConnection();  
			connection.setAllowUserInteraction(true);  
			connection.setRequestMethod("GET");  
			connection.setDoInput(true);
			connection.setDoOutput(true);  
			connection.setUseCaches(false); 
			System.out.println("Msg: "+ connection.getResponseMessage());
			System.out.println("Error code: "+ connection.getResponseCode());

			DataInputStream in = new DataInputStream (connection.getInputStream ()); 
//			BufferedReader in = new BufferedReader(
//					new InputStreamReader(connection.getInputStream()));

			String inputLine;
			while (null!=((inputLine = in.readLine()))){
//				System.out.println(inputLine);
			    returnString +=inputLine;
			}
			
			in.close();

		}catch(Exception e){  
			e.printStackTrace();  
		}  
		System.out.println("============"+returnString);
		return returnString;
	}

	public String deleteService(String recorduri){
		String urlStr=null;
		String returnString="";
		if (recorduri!=null)
			urlStr = this.urlStr+recorduri;  
		try{  
			URL url = new URL(urlStr);  

			HttpURLConnection connection= (HttpURLConnection)url.openConnection();  
			connection.setAllowUserInteraction(true);  
			connection.setRequestMethod("DELETE");  
			connection.setDoInput(true);
			connection.setDoOutput(true);  
			connection.setUseCaches(false);  

			System.out.println("Msg: "+ connection.getResponseMessage());
			System.out.println("Error code: "+ connection.getResponseCode());
			DataInputStream in = new DataInputStream (connection.getInputStream ()); 

			String inputLine;
			while (null!=((inputLine = in.readLine()))){
//				System.out.println(inputLine);
			    returnString +=inputLine;
			}
			in.close();

		}catch(Exception e){  
			e.printStackTrace();  
		}  
//		System.out.println("============"+returnString);
		return returnString;
	}

	public String renewService(String recorduri,String params){
		String urlStr=null;
		String [] words = null;
		String returnString="";
		words=params.trim().split(",");
		if (recorduri!=null&&params!=null){
			for(int i=0;i<words.length;i++){
				if(words[i].contains("record-ttl")){
					urlStr = this.urlStr+recorduri; 
					break;
				}
			}
		}
		System.out.println("urlstr"+urlStr);
		try{  
			URL url = new URL(urlStr);  
			HttpURLConnection connection= (HttpURLConnection)url.openConnection();  

			connection.setAllowUserInteraction(true);  
			connection.setRequestMethod("POST");  
			connection.setDoInput(true);
			connection.setDoOutput(true);  
			connection.setUseCaches(false);  


			DataOutputStream out=new DataOutputStream (connection.getOutputStream ()); 

			out.writeBytes(params);
			out.close();

			System.out.println("Msg: "+ connection.getResponseMessage());
			System.out.println("Error code: "+ connection.getResponseCode());
			DataInputStream in = new DataInputStream (connection.getInputStream ()); 

			String inputLine;
			while (null!=((inputLine = in.readLine()))){
//				System.out.println(inputLine);
			    returnString +=inputLine;
			}
			in.close();

		}catch(Exception e){  
			e.printStackTrace();  
		}  
		System.out.println("============"+returnString);
		return returnString;
	}

	public String registerService(String params){
		String returnString="";
		try{  	
			URL url = new URL(this.urlStrs); 
			HttpURLConnection connection= (HttpURLConnection)url.openConnection();  
			connection.setAllowUserInteraction(true);  
			connection.setRequestMethod("POST");  
			connection.setDoInput(true);
			connection.setDoOutput(true);  
			connection.setUseCaches(false);  
			connection.setRequestProperty("Content-type",
					"application/json");
			connection.setRequestProperty("Accept",
					"application/json");
			DataOutputStream out=new DataOutputStream (connection.getOutputStream ()); 

			out.writeBytes(params);
			out.close();

			System.out.println("Msg: "+ connection.getResponseMessage());
			System.out.println("Error code: "+ connection.getResponseCode());
			DataInputStream in = new DataInputStream (connection.getInputStream ()); 

			String inputLine;
			while (null!=((inputLine = in.readLine()))){
//				System.out.println(inputLine);
			    returnString +=inputLine;
			}
			in.close();

		}catch(Exception e){  
			e.printStackTrace();  
		}  
		System.out.println("============"+returnString);
		return returnString;
	}



	public String queryService(String message){
		String returnString="";
		String[] words;
		String []eachkey=new String[2];
		StringBuilder mes = new StringBuilder();
		String urlStr=null;

		if(message!=null){
			words=message.trim().split("&");
			for(int i =0;i<words.length;i++){
				eachkey=words[i].trim().split("=");
				System.out.println("eachkey:"+eachkey[0]);
				if(
//						eachkey[0].equals("record-type")||
//						eachkey[0].equals("record-service-type")||
//						eachkey[0].equals("record-service-domain")||
//						eachkey[0].equals("record-service-locator")||
//						eachkey[0].equals("record-privatekey")||
//						eachkey[0].equals("record-operator")||
//						eachkey[0].equals("record-service-type-operator")||
//						eachkey[0].equals("record-service-domain-operator")||
//						eachkey[0].equals("record-service-locator-operator")||
//						eachkey[0].equals("record-privatekey-operator")&&
						eachkey[1]!=null
						){
					if(eachkey[0].contains("operator")&&eachkey[1]=="any"||eachkey[1]=="all"){
						mes.append(eachkey[0]+"="+eachkey[1]+"&");
					}
					mes.append(eachkey[0]+"="+eachkey[1]+"&");
				}
			}
			System.out.println("MESSAGE:"+mes);
			urlStr=this.urlStrs+"?"+mes;
			System.out.println("url:"+urlStr);
		}
		try{  
			URL url = new URL(urlStr);  

			HttpURLConnection connection= (HttpURLConnection)url.openConnection();  
			connection.setAllowUserInteraction(true);  
			connection.setRequestMethod("GET");  
			connection.setDoInput(true);
			connection.setDoOutput(true);  
			connection.setUseCaches(false);  

			System.out.println("Msg: "+ connection.getResponseMessage());
			System.out.println("Error code: "+ connection.getResponseCode());
			DataInputStream in = new DataInputStream (connection.getInputStream ()); 

			String inputLine;
			while (null!=((inputLine = in.readLine()))){
//				System.out.println(inputLine);
			    returnString +=inputLine;
			}
			in.close();

		}catch(Exception e){  
			e.printStackTrace();  
		}  
		System.out.println("============"+returnString);
		return returnString;
	}


	public static void main(String[] args){  
		String urls="http://localhost:8080/lookup/services";
		String url="http://localhost:8080/lookup/service/";
//		String urls=null;
//		String url=null;
		String recorduri= "c1fca1cb-6fb7-4bfb-91e0-cad36f52a3bd";
		String key= "record-service-domain";
		String renewparams = "{\"record-ttl\":\"PT2H5M2S\",\"client-uuid\":[\"myuuid\"]}";
		String message = "record-service-type=ping&record-service-domain=ESnet,L*&record-service-locator=tcp://nash-pt1.es.net:4823&record-operator=any";
		String regparams="{\"record-type\":[\"service\"],\"record-service-locator\":[\"http://localhost/accesspointasjdfoddi\"],\"record-privatekey\":[\"myuuid\"],\"record-service-type\":[\"owamp\"],\"record-service-domain\":[\"es.net\"]}";
		LSClient client = new LSClient(url,urls);
//		client.getDataOnServer();
//		client.getService(recorduri);
//		client.getServiceKey(recorduri,key);
//		client.deleteService(recorduri);
//		client.renewService(recorduri,renewparams);
		client.queryService(message);
//		client.registerService(regparams);
	}  


}