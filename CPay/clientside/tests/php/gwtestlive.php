<?php

    /*
    * Make sure the argument includes: pay_in | pay_out | ....More to come
    * 
    */

    $base_url = "https://cpay.citotech.net/api/";
    //$merchant_number = "1000003";
    $merchant_number = "1000009";
    $amount = "500";
    $description = "Payment..";
    $payer_account = "256783086794";
    $callback_url = "";
    $privatekeyfile = "flixafric-private.pem";
    $publickeyfile = "flixafric-public.pem";


    if (isset($argv[1])) {
        $action = $argv[1];
        if ($action == "pay_in") {
            initiate_pay_in();
        } else if ($action == "pay_out") {
            initiate_pay_out();
        }
    }

    function initiate_pay_in() 
	{
        global $base_url, $merchant_number, $description, $amount, $payer_account, $callback_url, $privatekeyfile;
        $private_key_c = file_get_contents("keys/".$privatekeyfile);
        
        $url = $base_url."doMobileMoneyPayIn";
		
		$d=array(
			'merchant_number' => $merchant_number, 
			'payer_number' => $payer_account, 
			'reference' => "260966220217-0001-".rand(10, 1000), 
			'signature' => "", 
			'amount' => $amount, 
			'description' => $description,
			'callback_url' => "http://localhost:2020/api/testCallbackReception",
		);

		$data = $d['merchant_number'].$d['payer_number'].$d['amount'].
			$d['reference'].$d['description'];

        $pkeyid = openssl_pkey_get_private($private_key_c);
        if (is_bool($pkeyid)) {
            echo "\nPrivate Key failure\n";
            echo $private_key_c;
            echo "\n\n";
            exit("The private key content can't be parsed into a private key\n");
        }

        openssl_sign($data, $signature, $pkeyid, "sha256WithRSAEncryption");
        $d['signature'] = base64_encode($signature);
        //$d['signature'] = "";
		openssl_free_key($pkeyid);

		$res = request($url, json_encode($d));

		echo "Sent: \n";
		print_r(json_encode($d));
		echo "\n\n";

		echo "Returned:\n";
		print_r($res);
        echo "\n\n";
        
        echo "\nLocal Signature Verification\n";
        echo $data."\n";
        print_r(verify_signature($signature, $data));
        echo "\n\n**************\n\n";
    }


    function initiate_pay_out() 
	{
        global $base_url, $merchant_number, $description, $amount, $payer_account, $callback_url,$privatekeyfile;
        $private_key_c = file_get_contents("keys/".$privatekeyfile);
        
        $url = $base_url."doMobileMoneyPayOut";
		
		$d=array(
			'merchant_number' => $merchant_number, 
			'payee_number' => $payer_account, 
			'reference' => "260966220217-0001-".rand(10, 1000), 
			'signature' => "", 
			'amount' => $amount, 
			'description' => $description,
			'callback_url' => "http://localhost:2020/api/testCallbackReception",
		);

		$data = $d['merchant_number'].$d['payee_number'].$d['amount'].
			$d['reference'].$d['description'];

        $pkeyid = openssl_pkey_get_private($private_key_c);
        if (is_bool($pkeyid)) {
            echo "\nPrivate Key failure\n";
            echo $private_key_c;
            echo "\n\n";
            exit("The private key content can't be parsed into a private key\n");
        }

        openssl_sign($data, $signature, $pkeyid, "sha256WithRSAEncryption");
        $d['signature'] = base64_encode($signature);
        //$d['signature'] = "";
		openssl_free_key($pkeyid);

		$res = request($url, json_encode($d));

		echo "Sent: \n";
		print_r(json_encode($d));
		echo "\n\n";

		echo "Returned:\n";
		print_r($res);
        echo "\n\n";
        
        echo "\nLocal Signature Verification\n";
        echo $data."\n";
        print_r(verify_signature($signature, $data));
        echo "\n\n**************\n\n";
    }
    
    function verify_signature($signature, $data) 
    {
        global $privatekeyfile, $publickeyfile;
        $public_key_c = file_get_contents("keys/".$publickeyfile);
        $pkeyid = openssl_pkey_get_public($public_key_c);
        if (openssl_verify($data, $signature, $pkeyid, "sha256WithRSAEncryption")) {
            return "passed";
        } else {
            return "failed";
        }
    }

    function sign_data($data, $private_key) 
	{
		$pkeyid = openssl_pkey_get_private($private_key);
		openssl_sign($data, base64_decode($signature), $pkeyid);
		openssl_free_key($pkeyid);
		return $signature;
	}


    /*
    * Makes an HTTP request to the payment gateway server.
    * 
    * @Param Sring $url: This is the URL to call.
    * @Param String $data: This is the data to send in the request.
    * 
    * Returns Array[data, error]
    */
    function request($url, $data)
	{
		set_time_limit(0);
		$headers = array
		(
			'Content-type: text/json',
		);
		try {
			$ch = curl_init();
			curl_setopt($ch, CURLOPT_URL, $url);
			curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, FALSE);
			curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, FALSE);
			curl_setopt($ch, CURLOPT_RETURNTRANSFER, TRUE );
			curl_setopt($ch, CURLOPT_POST,           1 );
			curl_setopt($ch, CURLOPT_POSTFIELDS,     $data ); 
			curl_setopt($ch, CURLOPT_HTTPHEADER,     $headers ); 
			$response =curl_exec ($ch);
			$error = curl_error($ch);
			
			return array(
				"data" => trim($response),
				"error"=>$error
			);
		} catch (Exception $ex) {
			$error = $ex->getMessage();
			return array(
				"data" => "",
				"error"=>$error
			);
		}
	}
?>