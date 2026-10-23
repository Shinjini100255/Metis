from datetime import date,datetime,timezone,timedelta
from pydantic import BaseModel,Field,ConfigDict
from app.services.quality import calendar
from app.core.errors import DataProblem

class PlanRequest(BaseModel):
    model_config=ConfigDict(extra="forbid")
    start: date
    end: date
    existing: list[date]=Field(default_factory=list,max_length=5000)

def plan(request):
    if request.end<request.start or (request.end-request.start).days>1830 or request.start<date(2000,1,1):
        raise DataProblem("DATE_RANGE","Choose a supported date range of at most five years.",400)
    now=datetime.now(timezone.utc)
    end=min(request.end,now.date())
    if end<request.start:return {"missing_sessions":0,"missing_ranges":[],"compact_missing_sessions":0,"older_missing_sessions":0}
    cal=calendar()
    sessions=[str(d.date()) for d in cal.sessions_in_range(str(request.start),str(end)) if cal.session_close(d).to_pydatetime()<=now]
    cached={str(d) for d in request.existing}
    missing=[d for d in sessions if d not in cached]
    recent=[str(d.date()) for d in cal.sessions_in_range(str(now.date()-timedelta(days=190)),str(now.date())) if cal.session_close(d).to_pydatetime()<=now][-100:]
    ranges=[]
    for d in sessions:
        if d in cached:continue
        if not ranges or sessions.index(d)!=sessions.index(ranges[-1]["to"])+1:ranges.append({"from":d,"to":d})
        else:ranges[-1]["to"]=d
    compact=sum(d in recent for d in missing)
    return {"missing_sessions":len(missing),"missing_ranges":ranges[:100],"range_count":len(ranges),"compact_missing_sessions":compact,
            "older_missing_sessions":len(missing)-compact,"calendar":"XNYS","imputed_prices":0}
