package com.webhookmiddleman.models;

import org.json.JSONException;
import org.json.JSONObject;

public class Image
{

	private final String url;

	public Image(String url)
	{
		this.url = url;
	}

	public JSONObject get()
	{
		JSONObject obj = new JSONObject();
		try
		{
			obj.put("url", this.url);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
		return obj;
	}
}
