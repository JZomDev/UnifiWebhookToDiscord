package org.models;

import org.json.JSONException;
import org.json.JSONObject;

public class Footer
{

	private final String text;
	private final String icon_url;

	public Footer(String text, String icon_url)
	{
		this.text = text;
		this.icon_url = icon_url;
	}

	public JSONObject get()
	{
		JSONObject obj = new JSONObject();
		try
		{
			obj.put("text", this.text);
			obj.put("icon_url", this.icon_url);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
		return obj;
	}
}
