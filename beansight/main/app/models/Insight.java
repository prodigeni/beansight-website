package models;

import helpers.FormatHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import models.Filter.FilterType;
import models.Vote.State;
import models.Vote.Status;

import org.hibernate.annotations.Index;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;

import play.Logger;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.db.jpa.Model;
import play.modules.search.Field;
import play.modules.search.Indexed;
import play.modules.search.Query;
import play.modules.search.Search;
import play.templates.JavaExtensions;
import exceptions.InsightWithSameUniqueIdAndEndDateAlreadyExistsException;


@Indexed
@Entity
public class Insight extends Model {

	/** Number of hours after the deadline before the insight can be processed by the validation process */
	public static final int VALIDATION_HOUR_NUMBER = 72; 
	
	/** unique id randomly generated by us to insure nobody can guess an insight id  */
	@Index(name = "INSIGHT_UNIQUE_ID_IXD")
	public String uniqueId;
	
	@ManyToOne
	@Required
	public User creator;

	/** the date this insight has been created by its creator */
	// TODO index this (unfortunately simply adding @Field transforms the date
	// in string)
	public Date creationDate;

	/** the date this insight is ending, defined by its creator */
	// TODO index this
	@Required
	public Date endDate;

	/** Content of the insight, a simple text describing the idea */
	@Field
	@Required
	@MinSize(5)
	@MaxSize(140)
	@Lob
	public String content;

	/** the language of the content of this insight */
	@ManyToOne
	public Language lang;
	
	/** Every vote of the current insight */
	@OneToMany(mappedBy = "insight", cascade = CascadeType.ALL)
	public List<Vote> votes;

	/** Every tag of the current insight */
	@ManyToMany(mappedBy = "insights", cascade = CascadeType.ALL)
	@Field
	public List<Tag> tags;

	@ManyToOne
	@Field
	@Required
	/** Category of this insight */
	public Category category;

	/** Users who follow the current insight */
	@ManyToMany(mappedBy = "followedInsights", cascade = CascadeType.ALL)
	public List<User> followers;

	/** Comments made to current insight */
	@OneToMany(mappedBy = "insight", cascade = CascadeType.ALL)
	@OrderBy("creationDate DESC")
	public List<Comment> comments;

	public boolean hidden;
	
	// model denormalization :
	// having to count agree and disagree each time you need to access an
	// insight is a performance killer
	/**
	 * current number of active "agree" votes (if someone changed his mind, it
	 * is not counted)
	 */
	public long agreeCount;
	/**
	 * current number of active "disagree" votes (if someone changed his mind,
	 * it is not counted)
	 */
	public long disagreeCount;
	/** the last time when someone voted for the insight */
	public Date lastUpdated;

	@OneToMany(mappedBy = "insight", cascade = CascadeType.ALL)
	@OrderBy(value = "trendDate")
	public List<Trend> trends;
	
	/** has this insight been validated by the ValidationJob ? */
	public boolean validated;
	
	/** True ? False ? Can't say ? Number between 0 and 1 representing the decided validation of this insight. */
	public double validationScore;

	/** Probability this insight has to occure before its endDate */
	public double occurenceScore;
	
	/**
	 * Create an insight
	 * 
	 * @param creator
	 * @param content
	 *            : content text of this insight
	 * @param endDate
	 *            : date this insight is supposed to end
	 * @param category
	 *            : the category of the insight
	 */
	public Insight(User creator, String content, Date endDate, Category category, Language lang) throws InsightWithSameUniqueIdAndEndDateAlreadyExistsException {
		this.uniqueId = JavaExtensions.slugify(content);
		// We insure that there's no insight having the same uniqueId
		// (This can happen since the uniqueId is the "slugified" of the insight content)
		Insight existingInsight = Insight.findByUniqueId(uniqueId);
		
		if (existingInsight != null) {
			if (existingInsight.endDate.getTime() == endDate.getTime()) {
				throw new InsightWithSameUniqueIdAndEndDateAlreadyExistsException();
			} else {
				// adding the date at the end of the uniqueId :
				this.uniqueId += "-" + FormatHelper.formatDate(endDate);
			}
		}
 
		this.creator = creator;
		this.creationDate = new Date();
		this.endDate = endDate;
		this.content = content;
		this.followers = new ArrayList<User>();
		this.comments = new ArrayList<Comment>();
		this.category = category;
		this.trends = new ArrayList<Trend>();
		this.lastUpdated = new Date();
		this.lang = lang;
		this.validated = false;
		this.validationScore = 0.5;
		this.occurenceScore = 0.5;
		this.hidden = false;
	}
	
	/**
	 * Tells if the current insight was created by the given User
	 * 
	 * @param user
	 * @return
	 */
	public boolean isCreator(User user) {
		if (creator.equals(user)) {
			return true;
		}
		return false;
	}

	/**
	 * a user adds tags from an input string.
	 * 
	 * @param tagLabelList
	 *            : list of tag labels separated by commas and spaces
	 * @param user
	 *            : the user adding the tag
	 */
	public void addTags(String tagLabelList, User user) {
		String[] labelArray = tagLabelList.split(",");

		for (int i = 0; i < labelArray.length; i++) {
			String label = labelArray[i].trim();
			this.addTag(label, user);
		}
	}

	/**
	 * Add a tag from a given label string, will check if tag already exists for
	 * this insight
	 * 
	 * @param label
	 *            : the label of the tag (will not be processed)
	 * @param user
	 *            : the user adding the tag
	 */
	private void addTag(String label, User user) {
		// TODO call here a method to normalize the label

		// check if this tag already exist for this insight
		boolean foundTag = false;
		if (this.tags != null) {
			for (Tag storedTag : this.tags) {
				if (storedTag.label.equalsIgnoreCase(label)) {
					storedTag.users.add(user);
					storedTag.save();
					foundTag = true;
					break;
				}
			}
		}
		// if not, check if this tag already exist on the website
		if (!foundTag) {
			Tag existTag = Tag.find("byLabel", label).first();
			if (existTag == null) {
				// if null, then create it.
				Tag newTag = new Tag(label, this, user);
				newTag.save();
			} else {
				// if found, then associate with this insight and this user.
				existTag.insights.add(this);
				existTag.users.add(user);
				existTag.save();
			}
		}
	}

	/**
	 * Add a comment to the current insight
	 * 
	 * @param content
	 * @param user
	 */
	public Comment addComment(String content, User user) {
		Comment comment = new Comment(user, this, content);
		comment.save();
		return comment;
	}

	/**
	 * get the list of the n last active votes for this Insight
	 * 
	 * @param n
	 *            : the maximum number of votes to return
	 * @return: the list of n most recent active votes
	 */
	public List<Vote> getLastVotes(int n) {
		return Vote.find(
				"select v from Vote v " + "join v.insight i "
						+ "where v.status = :status and i.id=:insightId "
						+ "order by v.creationDate DESC").bind("status",
				Status.ACTIVE).bind("insightId", this.id).fetch(n);
	}
	
	/**
	 * @return the list of the votes before the considered date for this Insight
	 */
	
	public List<Vote> getVotesBefore(Date date) {
		return Vote.find(
				"select v from Vote v " + "join v.insight i "
						+ "where i.id=:insightId "
						+ "and v.creationDate < :date").bind("insightId", this.id).bind("date", date).fetch();
	}

	/**
	 * @return the votes made on this insight before the end date, the older one is first
	 */
	public List<Vote> getChronologicalVotes() {
		return Vote.find(
				"select v from Vote v " + "join v.insight i "
						+ "where i.id=:insightId "
						+ "and v.creationDate < :date "
						+ "order by v.creationDate ASC").bind("insightId", this.id).bind("date",this.endDate).fetch();
	}
	
	/**
	 * @return the list of the users who voted on this insight
	 */
	public List<User> getUsers() {
		return User.find(
				"select u from User u " + "join u.votes v " + "join v.insight i "
						+ "where v.status = :status and i.id=:insightId"
						).bind("status", Status.ACTIVE)
						.bind("insightId", this.id)
						.fetch();
	}

	/**
	 * @return the ordered list of trends for this insight
	 */
	public List<Trend> getTrends() {
		return Trend.find(
				"select u from Trend u "
						+ "where u.insight.id=:insightId "
						+ "order by u.trendDate DESC"
						).bind("insightId", this.id).fetch();
	}
	
	public List<InsightTrend> getInsightTrends() {
		return InsightTrend.find(
				"select u from InsightTrend u "
						+ "where u.insight.id=:insightId "
						+ "order by u.trendDate ASC"
						).bind("insightId", this.id).fetch();
	}
	
	/**
	 * validate this insight : compute its final probability of occurrence (score) and set "validated" to true 
	 */
	public void validate() {
		this.computeScore();
		this.validationScore = this.occurenceScore;
		this.validated = true;
		this.save();
	}
	
	/**
	 * Compute the current probability of occurrence (score) of this insight
	 */
	public void computeScore () {
        double score = 0.5;
        // score = ( sum position * DT ) / ( sum DT )
        // DT = (timestamp position) - (timestamp creation) 
        // position = 1 if agree, 0 if disagree

    	double num = 0;
    	double denum = 0;
    	
        for(Vote vote : this.votes) {
        	double dt = vote.creationDate.getTime() - this.creationDate.getTime();
        	if(vote.state.equals(Vote.State.AGREE)) {
        		num += dt;
        	}
        	denum += dt;
        }
        if(denum > 0) {
        	score = num / denum;
        } else {
        	score = 0.5;
        }
        this.occurenceScore = score;
        this.save();
	}
	
	/**
	 * Compute the score for this insight of the users who voted on this insight
	 */
	public void computeVoterScores() {
		Logger.info(this.content); // to be removed
		
		// compute the evolution of the probability to occur for this insight over time
		this.buildInsightTrends();
		
		// check whether the prediction happened or not
		State hasHappened = State.AGREE;
		if (this.validationScore < 0.5) {
			hasHappened = State.DISAGREE;
		} 
		
		// if validation score is close to 0.5 then less point should be attributed
		// indexConfidence quantifies that
		double indexConfidence = 2 * Math.abs(this.validationScore - 0.5);
		
		// the more votes the more points!
		// TODO: add favorites and voters after deadline
		List<User> usersToProcess = this.getUsers();
		double popularity = usersToProcess.size();
		
		// deadline is the end of the prediction
		Date deadline = this.endDate;
		
		// lastdate in the algorithm fix till what date scores have been computed
		Date lastdate = this.creationDate;
		Date upperbound = this.endDate;
		boolean isLastaTrend = false;
		
		// votesToProcess lists the votes for each voter in the insight  
		List<Vote> votesToProcess;
		// trend is the list of the probabilities to occur for this insight over time
		List<InsightTrend> trend = this.getInsightTrends();
		
		// positionAtDate determines what was the likeliness of the prediction to occur at the date considered
		State positionAtDate;
		
		// votePointer and trendPointer point where we are in voteToProcess and in trend 
		int votePointer=0;
		int trendPointer=0;
		
		for (User userToProcess : usersToProcess) {
			// initialize everything
			trendPointer=0;
			votePointer=0;
			votesToProcess = userToProcess.getVotesToInsight(this);
			double score = 0;
			
			//let's check if there are votes before the deadline
			if(votesToProcess.isEmpty() || trend.isEmpty()){			
				//no scoring
			}else if(deadline.compareTo(votesToProcess.get(0).creationDate) < 0){
				// no scoring
			}else{
				lastdate = votesToProcess.get(0).creationDate;
				// now we will point to the trend directly before the first vote
				while(trend.get(trendPointer).trendDate.compareTo(votesToProcess.get(0).creationDate)<=0){
					trendPointer++;
					if(trendPointer == trend.size()-1){
						break;
					}
				}
				if(trendPointer>0){
					trendPointer--;
				}
				isLastaTrend=false;
				// at last let's compute scores! the algorithm stops when lasupdate=deadline
				// note the the last trend from an insight is at its deadline
				while(lastdate.compareTo(deadline)<0){
					positionAtDate = votesToProcess.get(votePointer).state;
					double probability = trend.get(trendPointer).occurenceProbability;
					// for each iteration we will check from what point till what point score should be computed
					// the lower bound is lastdate, the higher bound is the closest date after lastdate
					
					if(! isLastaTrend){ // Last update had a vote for upperbound. This means that now this vote is the lower bound.
						if(votePointer == votesToProcess.size()-1){ //if this the last vote, don't check next vote
							upperbound = trend.get(trendPointer+1).trendDate;
							isLastaTrend=true;
							trendPointer++;
						}else{ // the upperbound will be the next trend or next vote
							if(trend.get(trendPointer+1).trendDate.compareTo(votesToProcess.get(votePointer+1).creationDate)<0){
								upperbound = trend.get(trendPointer+1).trendDate;
								isLastaTrend=true;
								trendPointer++;
							}else{
								upperbound=votesToProcess.get(votePointer+1).creationDate;
								isLastaTrend=false;
								votePointer++;
							}
						}
					}else{ //last update had a trend for upperbound. This means that now, this trend is the lower  bound
						if(votePointer == votesToProcess.size()-1){ //if this the last vote, don't check next vote
							upperbound = trend.get(trendPointer+1).trendDate;
							isLastaTrend=true;
							trendPointer++;
						}else{ // the upperbound will be thenext vote or next trend
							if(trend.get(trendPointer+1).trendDate.compareTo(votesToProcess.get(votePointer+1).creationDate)<0){
								upperbound = trend.get(trendPointer+1).trendDate;
								isLastaTrend=true;
								trendPointer++;
							}else{
								upperbound=votesToProcess.get(votePointer+1).creationDate;
								isLastaTrend=false;
								votePointer++;
							}
						}
					}
					
					score += scoreCalculation(positionAtDate, hasHappened, lastdate, upperbound, probability);
					//Logger.info("" + this.id  + " / " + userToProcess.id + " / " + score);
					//Logger.info("" + positionAtDate  + " / " + hasHappened  + " / " + lastdate  + " / " + upperbound  + " / " + probability);
					lastdate = upperbound;
				}
				
				score = score * indexConfidence * popularity;
				Logger.info(userToProcess.userName + " : " + score);
			}
			
			// save computed score in UserInsightScore
			UserInsightScore userScore = userToProcess.getInsightScore(this);
			if( userScore == null ) {
				userScore = new UserInsightScore( userToProcess, this );
			}
			userScore.lastUpdate = new Date();
			userScore.score = score;
			userScore.save();
		}
	}
	
	/**
	 * function to compute the score associated with a given position (?)
	 * @param positionAtDate: position at date1
	 * @param hasHappened: final state of the insight
	 * @param positionDate: the date the position was taken
	 * @param date2
	 * @param occurenceProbability : probability the insight had to happen at the date
	 * @return
	 */
	private static double scoreCalculation(State positionAtDate,State hasHappened, Date positionDate, Date date2, double occurenceProbability){
		double rep = 0;
		double intHasHappened = 0;
		if(hasHappened.equals( State.AGREE )){
			intHasHappened = 1;
		}
		if (positionAtDate.equals( hasHappened ) ) {
			rep = Math.abs(intHasHappened - occurenceProbability) 
			* Math.abs(positionDate.getTime()-date2.getTime()) / (1000*60*60);
		} else {
			rep = - Math.abs(Math.abs(intHasHappened - 1) - occurenceProbability) 
			* Math.abs(positionDate.getTime()-date2.getTime()) / (1000*60*60);
		}
		return rep;
	}
	
	/**
	 * Creates the InsightTrends for this insight. Not more than one Insighttrend per hour.
	 */
	public void buildInsightTrends() {
		// delete all the existing trends
		InsightTrend.delete("insight = ?", this);

		List<Vote> votes = this.getChronologicalVotes();
		Vote vote;

		DateTime dateConsidered;
		// lastTrend is the date of the last trend built 
		DateTime lastTrendDate = new DateTime(this.creationDate);
		
		new InsightTrend(this.creationDate, this).save();
		
		// now for each vote we're going to build a trend
		// there shouldn't be more than one trend per hour
		// so for each vote we going to check if the next vote happened more than an hour after the last trend built
		// if next vote was created more than an hour after the last Trend we can use the vote for the following trend
		// if next vote was created less than an hour after the last Trend then we should skip this vote

		for (int i = 0; i < votes.size()-1; i++) {
			// vote is the considered vote
			vote = votes.get(i);
			// date considered is the date of the next vote
			dateConsidered = new DateTime(votes.get(i+1).creationDate);
			// if next vote was created more than an hour after the last Trend we can use the vote for the following trend
			if (dateConsidered.toDate().after(lastTrendDate.plusHours(1).toDate())) {
				// creation of the new trend
				new InsightTrend(vote.creationDate, this).save();
				// update lastTrend after the creation of a new trend
				lastTrendDate = new DateTime(vote.creationDate);
			}
		}
		// last vote is computed separately
		if (votes.size() > 0) {
			vote = votes.get(votes.size() - 1);
			dateConsidered = new DateTime(vote.creationDate);
			// if the last vote was created more than an hour after the last trend...
			if (this.endDate.after(dateConsidered.plusHours(1).toDate())) {
				// ...then a new trend should be created
				new InsightTrend(vote.creationDate, this).save();
			}
		}
		
		//and last, the last trend happens at the insight deadline
		new InsightTrend(this.endDate, this).save();
	}
	
	
	
	/**
	 * Performs a search action
	 * 
	 * @param query : the search query
	 * @param from : index of the first item to be returned
	 * @param number : number of items to return
	 * @return an object containing the result list and the total result
	 */
	public static InsightResult search(String query, int from, int number, Filter filter) {
		Category cat = null;
		if( ! filter.categories.isEmpty()) {
			for(Category catego : filter.categories) {
				cat = catego;
			}
		}
		
		// TODO Steren : this query string construction is temporary, we should better handle this
		String fullQueryString = "(content:" + query + " OR tags:" + query + ") ";
		if (cat != null) {
			fullQueryString += " AND category:" + cat.id;
		}
		Logger.info( "SEARCH:" + fullQueryString );

		Query q = Search.search(fullQueryString, Insight.class);

		// create the result object
		InsightResult result = new InsightResult();
		result.count = q.count();

		// restrict to a sub group
		q.page(from, number);

		result.results = q.fetch();

		return result;
	}

	/**
	 * @param from : index of the first item to be returned
	 * @param number : number of items to return
	 */
	public static InsightResult findLatest(int from, int number, Filter filter) {
        String query = "select i.id from Insight i "
        				+ "join i.tags t "
        				+ "where i.hidden is false "
				        + filter.generateJPAQueryWhereClause()
				        + " group by i.id "
				        + " order by i.lastUpdated DESC";

        List<Long> insightIds = Insight.find(query).from(from).fetch(number);

		InsightResult result = new InsightResult();
    	if(!insightIds.isEmpty()) {
    		result.results = Insight.find("select i from Insight i where i.id in (:idList) order by i.lastUpdated DESC").bind("idList", insightIds).fetch();
    	}
		
		return result;
	}
	
	/**
	 * Return the most voted insights from the previous 48 hours
	 * @param from
	 * @param length
	 * @param filter 
	 * @return
	 */
	public static InsightResult findTrending(int from, int length, Filter filter) {
		// First select the ids.
		String query = "select v.insight.id from Vote v "
						+ "join v.insight i "
						+ "join i.tags t "
						+ "where i.hidden is false "
						+ "and v.creationDate > ? " // Of course, do not check the status of the vote.
						+ filter.generateJPAQueryWhereClause()
						+ "group by v.insight.id "
						+ "order by count(v) desc";
		List<Long> insightIds = Insight.find(query, new DateTime().minusHours(48).toDate() ).from(from).fetch(length);
		
		InsightResult result = new InsightResult();
    	if(!insightIds.isEmpty()) {
    		result.results = Insight.find("select i from Insight i where i.id in (:idList)").bind("idList", insightIds).fetch();
    	}

		return result;
	}
	
	/**
	 * @param from : index of the first item to be returned
	 * @param number : number of items to return
	 */
	public static InsightResult findIncoming(int from, int number, Filter filter) {
        String query = "select i.id from Insight i "
		        		+ "join i.tags t "
		        		+ "where i.hidden is false "
		        		+ "and endDate >= :currentDate "
		        		+ filter.generateJPAQueryWhereClause()
		        		+ "group by i.id "
		        		+ "order by endDate ASC";

		List<Long> insightIds = Insight.find(query).bind("currentDate", new DateMidnight().toDateTime().minusMinutes(1).toDate()).from(from).fetch(number);

		InsightResult result = new InsightResult();
    	if(!insightIds.isEmpty()) {
    		result.results = Insight.find("select i from Insight i where i.id in (:idList) order by endDate ASC").bind("idList", insightIds).fetch();
    	}
		
		return result;
	}

	/**	
	 * Finds the insights whose date is over by 3 days, that haven't been validated yet 
	 * @param page : the page number to start from
	 * @param number : number of items per page
	 */
	public static List<Insight> findInsightsToValidate(int page, int number) {
		List<Insight> insights = Insight.find("hidden is false and validated is false and endDate < ?", new DateTime().minusHours(VALIDATION_HOUR_NUMBER).toDate()  ).fetch(page, number);
		return insights;
	}
	
	/**
	 * Returns insights not hidden (not deleted) 
	 * and having their target date after the current date
	 * 
	 * @param page : the page number to start from
	 * @param number : number of items per page
	 */
	public static List<Insight> findEndDateNotOver(int page, int number) {
		List<Insight> insights = Insight.find("hidden is false and endDate > ?", new Date()).fetch(page, number);
		return insights;
	}
	
	/**
	 * Create a snapshot of this insight state, store it in a Trend
	 */
	public void createTrendSnapshot() {
		this.trends.add(new Trend(new Date(), this, this.agreeCount, this.disagreeCount));
	}
	
    public long getTrendCount() {
    	return Trend.count("insight", this);
   }
	
    /**
     * 
     * 
     * @param horizontalDefinition Number of horizontal value that will be used to create the charts
     * @return
     */
    public List<Double> getAgreeRatioTrends(long horizontalDefinition) {
        long trendsCount = this.getTrendCount();
                
        List<Double> agreeTrends;
        if (trendsCount <= horizontalDefinition) {
            agreeTrends = find("select t.agreeRatio from Trend t join t.insight i where i.id = :insightId order by t.trendDate").bind("insightId", this.id)
                    .fetch();
        } else {
        	// FIXME : be careful, the "-2" make it possible for the value to be 0
        	long incrementSize = (trendsCount - 2) / horizontalDefinition;
            List<Long> indexList = new ArrayList<Long>((int)horizontalDefinition);
            for (int i = 1 ; i<horizontalDefinition ; i++) {
                indexList.add(i * incrementSize + 1);
            }
            
            agreeTrends = find(
                    "select t.agreeRatio from Trend t join t.insight i where i.id = :insightId and t.relativeIndex in (:indexList) order by t.trendDate")
                    .bind("insightId", this.id).bind("indexList", indexList).fetch();
        }

        return agreeTrends;
    }
    
    /**
     * Get an insight using its uniqueId
     * @param uniqueId
     * @return
     */
    public static Insight findByUniqueId(String uniqueId) {
    	return find("select i from Insight i where i.uniqueId = :uniqueId and i.hidden is false").bind("uniqueId", uniqueId).first();
    }
    
    public List<Comment> getNotHiddenComments() {
    	return Comment.find("select c from Comment c where c.insight.id = :insightId and c.hidden is false order by c.creationDate desc").bind("insightId", this.id).fetch();
    }
    
    /**
     * TODO : si from est null alors utiliser la date du dernier trend existant
     * TODO : si to est null alors utiliser la date de l'instant d'exécution
     * @param from
     * @param to
     * @param period
     */
    public void buildTrends(DateTime from, DateTime to, int period) {
    	Logger.debug("building trends for insight.id=%s", this.id);
    	Trend.delete("insight = ?  and trendDate between ? and ?", this, from.toDate(), to.toDate());
    	long agree = 0;
    	long disagree = 0;
    	DateTime start = from;
    	DateTime end = from.plusHours(period);
    	while (end.isBefore(to)) {
    		List<Object[]> result = Vote.find("select v.state, count(v) from Vote v where v.insight=? and v.creationDate between ? and ? group by v.state", this, start.toDate(), end.toDate()).fetch();
    		for (Object[] o : result) {
    			if (o[0].equals(Vote.State.AGREE)) {
    				agree = agree + (Long)o[1];
    			} else {
    				disagree = disagree + (Long)o[1];
    			} 
    		}
    		Trend trend = new Trend(end.toDate(), this, agree, disagree);
    		trend.save();
        	start = end;
        	end = end.plusHours(period);
    	}
    }
    
	public static class InsightResult {
		/** The asked insights */
		public List<Insight> results;
		/** the total number of results */
		public long count;
	}

	public String toString() {
		return content;
	}
}
