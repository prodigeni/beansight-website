package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import controllers.APIInsights.InsightItem;

import models.Category;
import models.Insight.InsightResult;
import models.User;
import models.UserCategoryScore;
import models.UserInsightsFilter;
import play.data.validation.Max;
import play.data.validation.Min;
import play.mvc.Router;

public class APIUsers extends APIController {

	public static class Profile {
		public String userName;
		public String description;
		public String avatarSmall;
		public String avatarMedium;
		public String avatarLarge;
		public int successfulPredictionsCount;
		public List<String[]> scores = new ArrayList<String[]>();
	}
	
	/**
	 * Get profile information about the given User<br/>
	 * <b>response:</b> <code>{userName, description, avatarSmall, avatarMedium, avatarLarge, successfulPredictionsCount}</code>
	 *
	 * @param userName unique userName of the user
	 */
	public static void profile(String userName) {
		User user = User.findByUserName(userName);
		Profile profil = new Profile();
		profil.userName = user.userName;
		profil.description = user.description;
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("userName", userName);
		map.put("code", user.avatarHashCode());
		profil.avatarSmall = Router.getFullUrl("Application.showAvatarSmall", map);
		profil.avatarMedium = Router.getFullUrl("Application.showAvatarMedium", map);
		profil.avatarLarge = Router.getFullUrl("Application.showAvatarLarge", map);
		profil.successfulPredictionsCount = user.successfulPredictionCount;
		List<UserCategoryScore> userCategorieScores = user.getLatestCategoryScores();
		for (UserCategoryScore userCategorieScore : userCategorieScores) {
			profil.scores.add(new String[] {userCategorieScore.category.label, userCategorieScore.normalizedScore.toString()});
		}
		
		renderAPI(profil);
	}
	
	
	/**
	 * Get a list of users who are followed by the given user<br/>
	 * <b>response:</b> <code>[userName, ...]</code>
	 * 
	 * @param userName unique userName of the user
	 */
	public static void friends(String userName) {
		checkAccessToken();
		
		User user = User.findByUserName(userName);
		
		List<String> friends = new ArrayList<String>();
		for (User friend : user.followedUsers) {
			friends.add(friend.userName);
		}
		
		renderAPI(friends);
	}
	
	
	/**
	 * Get a list of the followers of the given user<br/>
	 * <b>response:</b> <code>[userName, ...]</code>
	 * 
	 * @param userName unique userName of the user
	 */
	public static void followers(String userName) {
		checkAccessToken();
		
		User user = User.findByUserName(userName);
		
		List<String> followers = new ArrayList<String>();
		for (User follower : user.followers) {
			followers.add(follower.userName);
		}
		
		renderAPI(followers);
	}
	/**
	 * Get a list of the insights a given user interacted with.<br/>
	 * <b>response:</b> <code>[{id, content, creationDate, endDate, creator, category, agreeCount, disagreeCount, commentCount, lastCurrentUserVote}, ...]</code>
	 * 
	 * @param userName unique userName of the user
	 * @param from
	 *            index of the first insight to return, default = 0
	 * @param number
	 *            number of insights to return, default = 20
	 * @param category
	 *            id of the category to restrict to, default = null
	 * @param actions insights voted or created by the given user. ["voted", "created"] (String), default = "voted"
	 */
	public static void insights(String userName, @Min(0) Integer from, @Min(1) @Max(100) Integer number, Integer category, String actions) {
		if (validation.hasErrors()) {
			badRequest();
		}
		if (from == null) {
			from = 0;
		}
		if (number == null) {
			number = 20;
		}
		if (actions == null || actions.trim().equals("")) {
			actions = "voted";
		}
		
		User user = User.findByUserName(userName);
		InsightResult result = Application.getFilteredUserInsightsList(from, Application.NUMBER_INSIGHTS_INSIGHTPAGE, category, user, actions);
		
		renderAPI(InsightItem.insightListToInsightItemList(result.results));
	}
	
}
