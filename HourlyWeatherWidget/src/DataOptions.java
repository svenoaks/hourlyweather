
public enum DataOptions
{
	PRECIP_INTENSITY("Precip Intensity"), PRECIP_PROBABILITY("Precip Probability"), 
	TEMPERATURE("Temperature"), APPARENT_TEMPERATURE("Apparent Temp"), WIND_SPEED("Wind Speed");
	
	private String friendly;
	
	private DataOptions(String friendly)
	{
		this.friendly = friendly;
	}
	@Override
	public String toString()
	{
		return friendly;
	}
}
